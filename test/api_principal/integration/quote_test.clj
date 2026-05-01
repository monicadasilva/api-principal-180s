(ns api-principal.integration.quote-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ring.mock.request :as mock]
            [muuntaja.core :as m]
            [api-principal.integration.fixtures :as f]))

(use-fixtures :each f/clean-db)

(defn json-request [method path body]
  (-> (mock/request method path)
      (mock/json-body body)))

(defn parse-body [response]
  (m/decode "application/json" (:body response)))

(defn create-partner! [app]
  (let [response (app (json-request :post "/partners"
                                    {:name "Acme" :cnpj "12345678000195"}))]
    (parse-body response)))

(deftest create-quote
  (let [app        (f/test-app)
        partner    (create-partner! app)
        partner-id (:id partner)
        response   (app (json-request :post (str "/partners/" partner-id "/quotes")
                                      {:age 30 :sex "f"}))]
    (testing "returns 201 with quote data"
      (is (= 201 (:status response)))
      (is (= 30 (:age (parse-body response)))))))

(deftest create-quote-invalid-age
  (let [app        (f/test-app)
        partner    (create-partner! app)
        partner-id (:id partner)
        response   (app (json-request :post (str "/partners/" partner-id "/quotes")
                                      {:age 100 :sex "f"}))]
    (testing "returns 400 for age > 99"
      (is (= 400 (:status response))))))

(deftest create-quote-invalid-gender
  (let [app        (f/test-app)
        partner    (create-partner! app)
        partner-id (:id partner)
        response   (app (json-request :post (str "/partners/" partner-id "/quotes")
                                      {:age 30 :sex "x"}))]
    (testing "returns 400 for invalid sex"
      (is (= 400 (:status response))))))

(deftest create-quote-missing-keys
  (let [app        (f/test-app)
        partner    (create-partner! app)
        partner-id (:id partner)]
    (testing "returns 400 on missing age"
      (let [response (app (json-request :post (str "/partners/" partner-id "/quotes")
                                        {:sex "f"}))]
        (is (= 400 (:status response)))))))
