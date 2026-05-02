(ns api-principal.integration.quote-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [api-principal.integration.fixtures :as f]))

(use-fixtures :each f/clean-db)

(deftest create-quote
  (let [app       (f/test-app)
        partner   (f/create-partner! app "Acme" "12345678000195")
        api-key   (str (:api_key partner))
        partner-id (:id partner)
        response  (app (f/authed-json-request :post (str "/partners/" partner-id "/quotes")
                                              api-key {:age 30 :sex "f"}))]
    (testing "returns 201 with quote data"
      (is (= 201 (:status response)))
      (is (= 30 (:age (f/parse-body response)))))))

(deftest create-quote-unauthenticated
  (let [app       (f/test-app)
        partner   (f/create-partner! app "Acme" "12345678000195")
        partner-id (:id partner)
        response  (app (f/json-request :post (str "/partners/" partner-id "/quotes")
                                       {:age 30 :sex "f"}))]
    (testing "returns 401 without Bearer token"
      (is (= 401 (:status response))))))

(deftest create-quote-invalid-age
  (let [app       (f/test-app)
        partner   (f/create-partner! app "Acme" "12345678000195")
        api-key   (str (:api_key partner))
        partner-id (:id partner)
        response  (app (f/authed-json-request :post (str "/partners/" partner-id "/quotes")
                                              api-key {:age 100 :sex "f"}))]
    (testing "returns 400 for age > 99"
      (is (= 400 (:status response))))))

(deftest create-quote-invalid-gender
  (let [app       (f/test-app)
        partner   (f/create-partner! app "Acme" "12345678000195")
        api-key   (str (:api_key partner))
        partner-id (:id partner)
        response  (app (f/authed-json-request :post (str "/partners/" partner-id "/quotes")
                                              api-key {:age 30 :sex "x"}))]
    (testing "returns 400 for invalid sex"
      (is (= 400 (:status response))))))

(deftest create-quote-missing-keys
  (let [app       (f/test-app)
        partner   (f/create-partner! app "Acme" "12345678000195")
        api-key   (str (:api_key partner))
        partner-id (:id partner)]
    (testing "returns 400 on missing age"
      (let [response (app (f/authed-json-request :post (str "/partners/" partner-id "/quotes")
                                                 api-key {:sex "f"}))]
        (is (= 400 (:status response)))))))
