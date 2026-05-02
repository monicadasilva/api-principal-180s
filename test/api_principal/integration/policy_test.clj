(ns api-principal.integration.policy-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [api-principal.integration.fixtures :as f]))

(use-fixtures :each f/clean-db)

(defn- setup-partner! [app name cnpj]
  (let [p (f/create-partner! app name cnpj)]
    {:id      (:id p)
     :api-key (str (:api_key p))}))

(defn- create-quote! [app {:keys [id api-key]} age sex]
  (f/parse-body (app (f/authed-json-request :post (str "/partners/" id "/quotes")
                                            api-key {:age age :sex sex}))))

(defn- create-policy! [app {:keys [id api-key]} quote-id sex dob]
  (f/parse-body (app (f/authed-json-request :post (str "/partners/" id "/policies")
                                            api-key {:quotation_id  quote-id
                                                     :name          "João Silva"
                                                     :sex           sex
                                                     :date_of_birth dob}))))

(deftest create-policy
  (let [app     (f/test-app)
        partner (setup-partner! app "Acme" "12345678000195")
        quote   (create-quote! app partner 30 "f")
        response (app (f/authed-json-request :post (str "/partners/" (:id partner) "/policies")
                                             (:api-key partner)
                                             {:quotation_id  (:id quote)
                                              :name          "João Silva"
                                              :sex           "f"
                                              :date_of_birth "1996-03-15"}))]
    (testing "returns 200 with policy data from insurer"
      (is (= 200 (:status response))))))

(deftest create-policy-unauthenticated
  (let [app      (f/test-app)
        partner  (setup-partner! app "Acme" "12345678000195")
        quote    (create-quote! app partner 30 "f")
        response (app (f/json-request :post (str "/partners/" (:id partner) "/policies")
                                      {:quotation_id  (:id quote)
                                       :name          "João Silva"
                                       :sex           "f"
                                       :date_of_birth "1996-03-15"}))]
    (testing "returns 401 without Bearer token"
      (is (= 401 (:status response))))))

(deftest create-policy-unknown-quotation
  (let [app     (f/test-app)
        partner (setup-partner! app "Acme" "12345678000195")
        response (app (f/authed-json-request :post (str "/partners/" (:id partner) "/policies")
                                             (:api-key partner)
                                             {:quotation_id  (str (random-uuid))
                                              :name          "João Silva"
                                              :sex           "f"
                                              :date_of_birth "1996-03-15"}))]
    (testing "returns 404 when quotation does not exist"
      (is (= 404 (:status response))))))

(deftest create-policy-wrong-partner-quote
  (let [app      (f/test-app)
        partner-a (setup-partner! app "Acme"  "12345678000195")
        partner-b (setup-partner! app "Other" "98765432000100")
        quote    (create-quote! app partner-a 30 "f")
        response (app (f/authed-json-request :post (str "/partners/" (:id partner-b) "/policies")
                                             (:api-key partner-b)
                                             {:quotation_id  (:id quote)
                                              :name          "João Silva"
                                              :sex           "f"
                                              :date_of_birth "1996-03-15"}))]
    (testing "returns 404 when quotation belongs to a different partner"
      (is (= 404 (:status response))))))

(deftest create-policy-sex-mismatch
  (let [app     (f/test-app)
        partner (setup-partner! app "Acme" "12345678000195")
        quote   (create-quote! app partner 30 "f")
        response (app (f/authed-json-request :post (str "/partners/" (:id partner) "/policies")
                                             (:api-key partner)
                                             {:quotation_id  (:id quote)
                                              :name          "João Silva"
                                              :sex           "m"
                                              :date_of_birth "1996-03-15"}))]
    (testing "returns 422 when sex does not match quotation"
      (is (= 422 (:status response))))))

(deftest get-policy
  (let [app     (f/test-app)
        partner (setup-partner! app "Acme" "12345678000195")
        quote   (create-quote! app partner 30 "f")
        policy  (create-policy! app partner (:id quote) "f" "1996-03-15")
        response (app (f/authed-request :get (str "/partners/" (:id partner) "/policies/" (:id policy))
                                        (:api-key partner)))]
    (testing "returns 200 with policy"
      (is (= 200 (:status response))))))

(deftest get-policy-wrong-partner
  (let [app      (f/test-app)
        partner-a (setup-partner! app "Acme"  "12345678000195")
        partner-b (setup-partner! app "Other" "98765432000100")
        quote    (create-quote! app partner-a 30 "f")
        policy   (create-policy! app partner-a (:id quote) "f" "1996-03-15")
        response (app (f/authed-request :get (str "/partners/" (:id partner-b) "/policies/" (:id policy))
                                        (:api-key partner-b)))]
    (testing "returns 404 when policy belongs to a different partner"
      (is (= 404 (:status response))))))
