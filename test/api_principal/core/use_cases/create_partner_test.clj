(ns api-principal.core.use-cases.create-partner-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-principal.core.use-cases.create-partner :as create-partner]))

(defn- repo-with [overrides]
  (merge {:save-partner! (fn [_] :ok)} overrides))

(deftest invalid-cnpj-returns-422
  (let [res (create-partner/execute (repo-with {}) {:name "Acme" :cnpj "12345678000100"})]
    (is (= 422 (:status res)))
    (is (= "Invalid CNPJ" (-> res :body :error)))))

(deftest valid-cnpj-returns-201
  (let [res (create-partner/execute (repo-with {}) {:name "Acme" :cnpj "12345678000195"})]
    (is (= 201 (:status res)))))

(deftest response-includes-api-key-not-hash
  (let [res (create-partner/execute (repo-with {}) {:name "Acme" :cnpj "12345678000195"})]
    (testing "api-key is present in response"
      (is (some? (-> res :body :api-key))))
    (testing "api-key-hash is not leaked in response"
      (is (nil? (-> res :body :api-key-hash))))))

(deftest save-partner!-receives-no-api-key
  (let [saved (atom nil)
        repo  (repo-with {:save-partner! (fn [p] (reset! saved p))})
        _     (create-partner/execute repo {:name "Acme" :cnpj "12345678000195"})]
    (testing "api-key is stripped before persisting"
      (is (nil? (:api-key @saved))))
    (testing "api-key-hash is persisted"
      (is (some? (:api-key-hash @saved))))))

(deftest save-partner!-exception-propagates
  (let [repo (repo-with {:save-partner! (fn [_] (throw (ex-info "cnpj already exists"
                                                                 {:type :error/conflict})))})]
    (testing "conflict exception is not swallowed by the use case"
      (is (thrown? clojure.lang.ExceptionInfo
                   (create-partner/execute repo {:name "Acme" :cnpj "12345678000195"}))))))
