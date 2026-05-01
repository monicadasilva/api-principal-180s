(ns api-principal.adapters.outbound.insurer.client
  (:require [hato.client :as http]
            [clojure.core.memoize :as memo]
            [taoensso.telemere :as t]
            [api-principal.adapters.outbound.insurer.auth :as auth]))

(defn- auth-header [token]
  {"Authorization" (str "Bearer " token)})

(defn- with-retry [f get-token! retries base-ms]
  (let [result (try (f) (catch Exception e (t/error! ::insurer-error e) {:status 500}))]
    (cond
      (= 401 (:status result))
      (do (memo/memo-clear! get-token!)
          (f))

      (and (= 500 (:status result)) (pos? retries))
      (do (Thread/sleep (long (* base-ms (Math/pow 2 (- 3 retries)))))
          (with-retry f get-token! (dec retries) base-ms))

      :else result)))

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
       (with-retry
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
         get-token! 2 200))

     :get-policy
     (fn [policy-id]
       (with-retry
         #(http/get (str base-url "/api/policies/" policy-id)
                    {:http-client      http-client
                     :headers          (auth-header (get-token!))
                     :throw-exceptions false
                     :as               :json})
         get-token! 2 200))}))
