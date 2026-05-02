(ns api-principal.integration.partner-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [api-principal.integration.fixtures :as f]))

(use-fixtures :each f/clean-db)

(deftest create-partner
  (let [app      (f/test-app)
        response (app (f/json-request :post "/partners" {:name "Acme" :cnpj "12.345.678/0001-95"}))]
    (testing "returns 201 with partner data including api_key"
      (is (= 201 (:status response)))
      (let [body (f/parse-body response)]
        (is (= "Acme" (:name body)))
        (is (some? (:api_key body)))))))

(deftest create-partner-duplicate-cnpj
  (let [app (f/test-app)]
    (f/create-partner! app "Acme" "12.345.678/0001-95")
    (let [response (app (f/json-request :post "/partners" {:name "Acme" :cnpj "12.345.678/0001-95"}))]
      (testing "returns 409 on duplicate cnpj"
        (is (= 409 (:status response)))))))

(deftest create-partner-invalid-cnpj-format
  (let [app      (f/test-app)
        response (app (f/json-request :post "/partners" {:name "Acme" :cnpj "123"}))]
    (testing "returns 400 on invalid cnpj format"
      (is (= 400 (:status response))))))

(deftest create-partner-invalid-cnpj-digits
  (let [app      (f/test-app)
        response (app (f/json-request :post "/partners" {:name "Acme" :cnpj "12.345.678/0001-00"}))]
    (testing "returns 422 when cnpj has valid format but wrong check digits"
      (is (= 422 (:status response))))))

(deftest create-partner-missing-keys
  (testing "returns 400 on missing name"
    (let [app      (f/test-app)
          response (app (f/json-request :post "/partners" {:cnpj "12.345.678/0001-95"}))]
      (is (= 400 (:status response)))
      (is (= {:error "Type coercion error" :details {:name "required"}} (f/parse-body response)))))

  (testing "returns 400 on missing cnpj"
    (let [app      (f/test-app)
          response (app (f/json-request :post "/partners" {:name "Acme"}))]
      (is (= 400 (:status response)))
      (is (= {:error "Type coercion error" :details {:cnpj "required"}} (f/parse-body response))))))
