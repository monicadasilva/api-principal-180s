(ns api-principal.integration.policy-test
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

(defn create-partner! [app name cnpj]
  (parse-body (app (json-request :post "/partners" {:name name :cnpj cnpj}))))

(defn create-quote! [app partner-id age sex]
  (parse-body (app (json-request :post (str "/partners/" partner-id "/quotes")
                                 {:age age :sex sex}))))

(defn create-policy! [app partner-id quote-id sex dob]
  (parse-body (app (json-request :post (str "/partners/" partner-id "/policies")
                                 {:quotation_id  quote-id
                                  :name          "João Silva"
                                  :sex           sex
                                  :date_of_birth dob}))))

(deftest create-policy
  (let [app        (f/test-app)
        partner-id (:id (create-partner! app "Acme" "12345678000195"))
        quote-id   (:id (create-quote! app partner-id 30 "f"))
        response   (app (json-request :post (str "/partners/" partner-id "/policies")
                                      {:quotation_id  quote-id
                                       :name          "João Silva"
                                       :sex           "f"
                                       :date_of_birth "1996-03-15"}))]
    (testing "returns 200 with policy data from insurer"
      (is (= 200 (:status response))))))

(deftest create-policy-unknown-quotation
  (let [app        (f/test-app)
        partner-id (:id (create-partner! app "Acme" "12345678000195"))
        response   (app (json-request :post (str "/partners/" partner-id "/policies")
                                      {:quotation_id  (str (random-uuid))
                                       :name          "João Silva"
                                       :sex           "f"
                                       :date_of_birth "1996-03-15"}))]
    (testing "returns 404 when quotation does not exist"
      (is (= 404 (:status response))))))

(deftest create-policy-wrong-partner-quote
  (let [app        (f/test-app)
        partner-a  (:id (create-partner! app "Acme"  "12345678000195"))
        partner-b  (:id (create-partner! app "Other" "98765432000100"))
        quote-id   (:id (create-quote! app partner-a 30 "f"))
        response   (app (json-request :post (str "/partners/" partner-b "/policies")
                                      {:quotation_id  quote-id
                                       :name          "João Silva"
                                       :sex           "f"
                                       :date_of_birth "1996-03-15"}))]
    (testing "returns 404 when quotation belongs to a different partner"
      (is (= 404 (:status response))))))

(deftest create-policy-sex-mismatch
  (let [app        (f/test-app)
        partner-id (:id (create-partner! app "Acme" "12345678000195"))
        quote-id   (:id (create-quote! app partner-id 30 "f"))
        response   (app (json-request :post (str "/partners/" partner-id "/policies")
                                      {:quotation_id  quote-id
                                       :name          "João Silva"
                                       :sex           "m"
                                       :date_of_birth "1996-03-15"}))]
    (testing "returns 422 when sex does not match quotation"
      (is (= 422 (:status response))))))

(deftest get-policy
  (let [app        (f/test-app)
        partner-id (:id (create-partner! app "Acme" "12345678000195"))
        quote-id   (:id (create-quote! app partner-id 30 "f"))
        policy-id  (:id (create-policy! app partner-id quote-id "f" "1996-03-15"))
        response   (app (mock/request :get (str "/partners/" partner-id "/policies/" policy-id)))]
    (testing "returns 200 with policy"
      (is (= 200 (:status response))))))

(deftest get-policy-wrong-partner
  (let [app        (f/test-app)
        partner-a  (:id (create-partner! app "Acme"  "12345678000195"))
        partner-b  (:id (create-partner! app "Other" "98765432000100"))
        quote-id   (:id (create-quote! app partner-a 30 "f"))
        policy-id  (:id (create-policy! app partner-a quote-id "f" "1996-03-15"))
        response   (app (mock/request :get (str "/partners/" partner-b "/policies/" policy-id)))]
    (testing "returns 404 when policy belongs to a different partner"
      (is (= 404 (:status response))))))
