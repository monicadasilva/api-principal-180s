(ns api-principal.integration.partner-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ring.mock.request :as mock]
            [muuntaja.core :as m]
            [api-principal.integration.fixtures :as f]))

(use-fixtures :each f/clean-db)

(def ^:private base-body {:name "Acme" :cnpj "12.345.678/0001-95"})

(defn json-request [method path body]
  (-> (mock/request method path)
      (mock/json-body body)))

(defn parse-body [response]
  (m/decode "application/json" (:body response)))

(deftest create-partner
  (let [app      (f/test-app)
        response (app (json-request :post "/partners" base-body))]
    (testing "returns 201 with partner data"
      (is (= 201 (:status response)))
      (is (= "Acme" (:name (parse-body response)))))))

(deftest create-partner-duplicate-cnpj
  (let [app  (f/test-app)]
    (app (json-request :post "/partners" base-body))
    (let [response (app (json-request :post "/partners" base-body))]
      (testing "returns 409 on duplicate cnpj"
        (is (= 409 (:status response)))))))

(deftest create-partner-invalid-cnpj
  (let [app      (f/test-app)
        response (app (json-request :post "/partners" (assoc base-body :cnpj "123")))]
    (testing "returns 400 on invalid cnpj"
      (is (= 400 (:status response))))))

(deftest create-partner-missing-keys
  (testing "returns 400 on missing name"
    (let [app      (f/test-app)
          response (app (json-request :post "/partners" (dissoc base-body :name)))]
      (is (= 400 (:status response)))
      (is (= {:error "Type coercion error" :details {:name "required"}} (parse-body response)))))

  (testing "returns 400 on missing cnpj"
    (let [app      (f/test-app)
          response (app (json-request :post "/partners" (dissoc base-body :cnpj)))]
      (is (= 400 (:status response)))
      (is (= {:error "Type coercion error" :details {:cnpj "required"}} (parse-body response))))))
