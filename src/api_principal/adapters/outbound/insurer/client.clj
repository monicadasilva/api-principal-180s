(ns api-principal.adapters.outbound.insurer.client
  "HTTP client for the insurer API.

   Retry policy: 5xx responses and synthetic 502/504 from network errors are
   retried with exponential backoff (max 2 retries, 200ms base). The 5xx-as-
   transient assumption holds because inputs are validated upstream by Malli,
   so 5xx cannot originate from malformed payloads in this app. 401 triggers
   a single retry with a refreshed JWT. 4xx is never retried.

   All error paths return body-bearing responses so JSON-parsing clients
   never see an empty body."
  (:require [hato.client :as http]
            [clojure.core.memoize :as memo]
            [taoensso.telemere :as t]
            [api-principal.adapters.outbound.insurer.auth :as auth])
  (:import (java.net ConnectException UnknownHostException)
           (java.net.http HttpTimeoutException)))

(defn- auth-header [token]
  {"Authorization" (str "Bearer " token)})

(defn- error-response [status code detail]
  {:status status :body {:error code :detail detail}})

(defn- ensure-body [{:keys [status body] :as result}]
  (if (and (>= status 500) (or (nil? body) (= "" body)))
    (assoc result :body {:error "insurer_error" :detail "Insurer returned an empty body"})
    result))

(defn- attempt [f]
  (try
    (ensure-body (f))
    (catch UnknownHostException e
      (t/error! ::insurer-dns-error e)
      (error-response 502 "insurer_unreachable" "Cannot resolve insurer host"))
    (catch ConnectException e
      (t/error! ::insurer-connect-error e)
      (error-response 502 "insurer_unreachable" "Insurer is not reachable"))
    (catch HttpTimeoutException e
      (t/error! ::insurer-timeout e)
      (error-response 504 "insurer_timeout" "Insurer request timed out"))
    (catch clojure.lang.ExceptionInfo e
      (if (= :error/insurer-auth-failed (-> e ex-data :type))
        (do (t/error! ::insurer-auth-failed e)
            (error-response 502 "insurer_auth_failed" "Failed to authenticate with insurer"))
        (do (t/error! ::insurer-error e)
            (error-response 502 "insurer_error" "Unexpected error contacting insurer"))))
    (catch Exception e
      (t/error! ::insurer-unknown-error e)
      (error-response 502 "insurer_error" "Unexpected error contacting insurer"))))

(defn- transient? [{:keys [status]}]
  (#{500 502 504} status))

(defn- with-retry [f get-token! retries base-ms]
  (let [result (attempt f)]
    (cond
      (= 401 (:status result))
      (do (memo/memo-clear! get-token!)
          (attempt f))

      (and (transient? result) (pos? retries))
      (do (Thread/sleep (long (* base-ms (Math/pow 2 (- 3 retries)))))
          (with-retry f get-token! (dec retries) base-ms))

      :else result)))

(defn- parse-uuid-field [response field]
  (if (and (= 200 (:status response)) (some-> response :body field))
    (update-in response [:body field] #(java.util.UUID/fromString %))
    response))

(defn make [base-url api-key http-client]
  (let [get-token! (auth/make-token-fn base-url api-key http-client)]
    {:create-quote!
     (fn [age gender]
       (with-retry
         #(http/post (str base-url "/api/quotations")
                     {:http-client      http-client
                      :headers          (auth-header (get-token!))
                      :content-type     :json
                      :form-params      {:age age :sex gender}
                      :throw-exceptions false
                      :as               :json})
         get-token! 2 200))

     :create-policy!
     (fn [quotation-id name gender date-of-birth]
       (-> (with-retry
             #(http/post (str base-url "/api/policies")
                         {:http-client      http-client
                          :headers          (auth-header (get-token!))
                          :content-type     :json
                          :form-params      {:quotation_id  quotation-id
                                             :name          name
                                             :sex           gender
                                             :date_of_birth date-of-birth}
                          :throw-exceptions false
                          :as               :json})
             get-token! 2 200)
           (parse-uuid-field :id)))

     :get-policy
     (fn [policy-id]
       (with-retry
         #(http/get (str base-url "/api/policies/" policy-id)
                    {:http-client      http-client
                     :headers          (auth-header (get-token!))
                     :throw-exceptions false
                     :as               :json})
         get-token! 2 200))}))
