(ns api-principal.adapters.outbound.insurer.client-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [hato.client :as http]
            [taoensso.telemere :as t]
            [api-principal.adapters.outbound.insurer.client :as client])
  (:import (java.net ConnectException UnknownHostException)
           (java.net.http HttpTimeoutException)))

(use-fixtures :once
  (fn [run-tests]
    (t/with-min-level :fatal (run-tests))))

(def ^:private base-url "http://insurer")
(def ^:private api-key  "key")
(def ^:private http-client :http-client)

(defn- token-response [_url _opts]
  {:status 200 :body {:access_token "tok"}})

(deftest create-quote!-happy-path
  (let [posts (atom [])
        post  (fn [url opts]
                (swap! posts conj url)
                (if (.endsWith url "/api/auth")
                  (token-response url opts)
                  {:status 200 :body {:id "abc" :age 30}}))]
    (with-redefs [http/post post]
      (let [{:keys [create-quote!]} (client/make base-url api-key http-client)
            response (create-quote! 30 "f")]
        (testing "returns insurer 200 response"
          (is (= 200 (:status response)))
          (is (= "abc" (-> response :body :id))))
        (testing "auth was fetched once and reused for the quote call"
          (is (= [(str base-url "/api/auth")
                  (str base-url "/api/quotations")] @posts)))))))

(deftest create-policy!-parses-uuid-id
  (let [pid "fd3261bc-9e3c-4d4e-acae-8c4d5dc60b79"
        post (fn [url opts]
               (if (.endsWith url "/api/auth")
                 (token-response url opts)
                 {:status 200 :body {:id pid}}))]
    (with-redefs [http/post post]
      (let [{:keys [create-policy!]} (client/make base-url api-key http-client)
            response (create-policy! "qid" "Joao" "f" "1996-03-15")]
        (testing ":id in body is converted to UUID on 200"
          (is (= 200 (:status response)))
          (is (= (java.util.UUID/fromString pid) (-> response :body :id))))))))

(deftest create-policy!-non-200-id-not-parsed
  (let [post (fn [url opts]
               (if (.endsWith url "/api/auth")
                 (token-response url opts)
                 {:status 422 :body {:error "invalid"}}))]
    (with-redefs [http/post post]
      (let [{:keys [create-policy!]} (client/make base-url api-key http-client)
            response (create-policy! "qid" "Joao" "f" "1996-03-15")]
        (testing "non-200 responses are returned as-is"
          (is (= 422 (:status response)))
          (is (= "invalid" (-> response :body :error))))))))

(deftest get-policy-happy-path
  (let [post token-response
        get  (fn [_url _opts] {:status 200 :body {:id "p1"}})]
    (with-redefs [http/post post http/get get]
      (let [{:keys [get-policy]} (client/make base-url api-key http-client)
            response (get-policy "p1")]
        (is (= 200 (:status response)))
        (is (= "p1" (-> response :body :id)))))))

(deftest retry-on-5xx-then-success
  (let [api-calls (atom 0)
        post      (fn [url _opts]
                    (cond
                      (.endsWith url "/api/auth")
                      {:status 200 :body {:access_token "tok"}}

                      :else
                      (do (swap! api-calls inc)
                          (if (< @api-calls 2)
                            {:status 500 :body {:error "boom"}}
                            {:status 200 :body {:id "x"}}))))]
    (with-redefs [http/post post]
      (let [{:keys [create-quote!]} (client/make base-url api-key http-client)
            response (create-quote! 30 "f")]
        (testing "retries transient 500 and eventually returns 200"
          (is (= 200 (:status response)))
          (is (= 2 @api-calls)))))))

(deftest retry-exhausted-returns-last-5xx
  (let [api-calls (atom 0)
        post      (fn [url _opts]
                    (if (.endsWith url "/api/auth")
                      (token-response url nil)
                      (do (swap! api-calls inc)
                          {:status 500 :body {:error "boom"}})))]
    (with-redefs [http/post post]
      (let [{:keys [create-quote!]} (client/make base-url api-key http-client)
            response (create-quote! 30 "f")]
        (testing "after 2 retries, returns the last 5xx response"
          (is (= 500 (:status response)))
          (is (= 3 @api-calls)))))))

(deftest empty-body-on-5xx-gets-synthetic-error
  (let [post (fn [url _opts]
               (if (.endsWith url "/api/auth")
                 (token-response url nil)
                 {:status 500 :body nil}))]
    (with-redefs [http/post post]
      (let [{:keys [create-quote!]} (client/make base-url api-key http-client)
            response (create-quote! 30 "f")]
        (testing "ensure-body fills empty 5xx body so JSON clients see something"
          (is (= 500 (:status response)))
          (is (= "insurer_error" (-> response :body :error))))))))

(deftest no-retry-on-4xx
  (let [api-calls (atom 0)
        post      (fn [url _opts]
                    (if (.endsWith url "/api/auth")
                      (token-response url nil)
                      (do (swap! api-calls inc)
                          {:status 400 :body {:error "bad request"}})))]
    (with-redefs [http/post post]
      (let [{:keys [create-quote!]} (client/make base-url api-key http-client)
            response (create-quote! 30 "f")]
        (testing "4xx responses are not retried"
          (is (= 400 (:status response)))
          (is (= 1 @api-calls)))))))

(deftest unknown-host-becomes-502
  (let [post (fn [url _opts]
               (if (.endsWith url "/api/auth")
                 (token-response url nil)
                 (throw (UnknownHostException. "no host"))))]
    (with-redefs [http/post post]
      (let [{:keys [create-quote!]} (client/make base-url api-key http-client)
            response (create-quote! 30 "f")]
        (is (= 502 (:status response)))
        (is (= "insurer_unreachable" (-> response :body :error)))))))

(deftest connect-exception-becomes-502
  (let [post (fn [url _opts]
               (if (.endsWith url "/api/auth")
                 (token-response url nil)
                 (throw (ConnectException. "refused"))))]
    (with-redefs [http/post post]
      (let [{:keys [create-quote!]} (client/make base-url api-key http-client)
            response (create-quote! 30 "f")]
        (is (= 502 (:status response)))
        (is (= "insurer_unreachable" (-> response :body :error)))))))

(deftest timeout-becomes-504
  (let [post (fn [url _opts]
               (if (.endsWith url "/api/auth")
                 (token-response url nil)
                 (throw (HttpTimeoutException. "slow"))))]
    (with-redefs [http/post post]
      (let [{:keys [create-quote!]} (client/make base-url api-key http-client)
            response (create-quote! 30 "f")]
        (is (= 504 (:status response)))
        (is (= "insurer_timeout" (-> response :body :error)))))))

(deftest auth-failure-becomes-502
  (let [post (fn [url _opts]
               (if (.endsWith url "/api/auth")
                 (throw (Exception. "auth boom"))
                 {:status 200 :body {}}))]
    (with-redefs [http/post post]
      (let [{:keys [create-quote!]} (client/make base-url api-key http-client)
            response (create-quote! 30 "f")]
        (is (= 502 (:status response)))
        (is (= "insurer_auth_failed" (-> response :body :error)))))))

(deftest unexpected-exception-becomes-502
  (let [post (fn [url _opts]
               (if (.endsWith url "/api/auth")
                 (token-response url nil)
                 (throw (RuntimeException. "weird"))))]
    (with-redefs [http/post post]
      (let [{:keys [create-quote!]} (client/make base-url api-key http-client)
            response (create-quote! 30 "f")]
        (is (= 502 (:status response)))
        (is (= "insurer_error" (-> response :body :error)))))))

(deftest on-401-token-is-refreshed-and-call-retried
  (let [auth-calls (atom 0)
        api-calls  (atom 0)
        post       (fn [url _opts]
                     (cond
                       (.endsWith url "/api/auth")
                       (do (swap! auth-calls inc)
                           {:status 200 :body {:access_token (str "tok-" @auth-calls)}})

                       :else
                       (do (swap! api-calls inc)
                           (if (= 1 @api-calls)
                             {:status 401 :body {:error "expired"}}
                             {:status 200 :body {:id "ok"}}))))]
    (with-redefs [http/post post]
      (let [{:keys [create-quote!]} (client/make base-url api-key http-client)
            response (create-quote! 30 "f")]
        (testing "401 clears token cache, refetches, and retries the call once"
          (is (= 200 (:status response)))
          (is (= 2 @auth-calls))
          (is (= 2 @api-calls)))))))
