(ns api-principal.adapters.outbound.insurer.auth-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [hato.client :as http]
            [taoensso.telemere :as t]
            [api-principal.adapters.outbound.insurer.auth :as auth]))

(use-fixtures :once
  (fn [run-tests]
    (t/with-min-level :fatal (run-tests))))

(deftest make-token-fn-fetches-token
  (let [calls (atom 0)]
    (with-redefs [http/post (fn [_url _opts]
                              (swap! calls inc)
                              {:status 200 :body {:access_token "tok-1"}})]
      (let [get-token! (auth/make-token-fn "http://insurer" "key" :http-client)]
        (testing "returns the access_token from the auth response"
          (is (= "tok-1" (get-token!))))
        (testing "memoized on subsequent calls within TTL"
          (get-token!)
          (get-token!)
          (is (= 1 @calls)))))))

(deftest make-token-fn-missing-access-token
  (with-redefs [http/post (fn [_url _opts] {:status 200 :body {}})]
    (let [get-token! (auth/make-token-fn "http://insurer" "key" :http-client)]
      (testing "throws insurer-auth-failed ex-info when access_token is absent"
        (try
          (get-token!)
          (is false "should have thrown")
          (catch clojure.lang.ExceptionInfo e
            (is (= :error/insurer-auth-failed (-> e ex-data :type)))))))))

(deftest make-token-fn-http-failure
  (with-redefs [http/post (fn [_url _opts] (throw (Exception. "boom")))]
    (let [get-token! (auth/make-token-fn "http://insurer" "key" :http-client)]
      (testing "wraps any HTTP failure as insurer-auth-failed ex-info"
        (try
          (get-token!)
          (is false "should have thrown")
          (catch clojure.lang.ExceptionInfo e
            (is (= :error/insurer-auth-failed (-> e ex-data :type)))))))))
