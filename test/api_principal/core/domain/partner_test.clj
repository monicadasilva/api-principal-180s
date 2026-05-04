(ns api-principal.core.domain.partner-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-principal.core.domain.partner :as partner]
            [api-principal.core.domain.api-key :as api-key]))

(deftest valid-cnpj?-test
  (testing "accepts valid CNPJ without formatting"
    (is (partner/valid-cnpj? "12345678000195"))
    (is (partner/valid-cnpj? "11222333000181")))

  (testing "accepts valid CNPJ with formatting"
    (is (partner/valid-cnpj? "12.345.678/0001-95"))
    (is (partner/valid-cnpj? "11.222.333/0001-81")))

  (testing "rejects wrong check digits"
    (is (not (partner/valid-cnpj? "12345678000100")))
    (is (not (partner/valid-cnpj? "12.345.678/0001-00"))))

  (testing "rejects wrong length"
    (is (not (partner/valid-cnpj? "1234567800019")))
    (is (not (partner/valid-cnpj? "123456780001950"))))

  (testing "rejects empty string"
    (is (not (partner/valid-cnpj? "")))))

(deftest build-returns-required-keys
  (let [p (partner/build "Acme" "12345678000195")]
    (testing "returns all required keys"
      (is (contains? p :id))
      (is (contains? p :name))
      (is (contains? p :cnpj))
      (is (contains? p :api-key))
      (is (contains? p :api-key-hash)))))

(deftest build-strips-cnpj-formatting
  (let [p (partner/build "Acme" "12.345.678/0001-95")]
    (testing "cnpj has only digits"
      (is (= "12345678000195" (:cnpj p))))))

(deftest build-api-key-hash-matches-digest
  (let [p (partner/build "Acme" "12345678000195")]
    (testing "api-key-hash is SHA-256 of api-key"
      (is (= (api-key/digest (:api-key p)) (:api-key-hash p))))))

(deftest build-generates-unique-ids
  (testing "two calls produce different ids and api-keys"
    (let [a (partner/build "Acme" "12345678000195")
          b (partner/build "Acme" "12345678000195")]
      (is (not= (:id a) (:id b)))
      (is (not= (:api-key a) (:api-key b))))))
