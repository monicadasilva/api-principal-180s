(ns api-principal.core.domain.policy-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-principal.core.domain.policy :as policy])
  (:import [java.time LocalDate]))

(deftest quote-expired?-test
  (testing "expired when expire-at is in the past"
    (is (policy/quote-expired? {:expire-at (LocalDate/of 2020 1 1)})))
  (testing "not expired when expire-at is in the future"
    (is (not (policy/quote-expired? {:expire-at (LocalDate/of 2099 12 31)})))))

(deftest age-matches-dob?-test
  (testing "exact match"
    (is (policy/age-matches-dob? 30 "1996-03-15")))
  (testing "within ±1 year tolerance"
    (is (policy/age-matches-dob? 29 "1996-03-15"))
    (is (policy/age-matches-dob? 31 "1996-03-15")))
  (testing "outside tolerance"
    (is (not (policy/age-matches-dob? 25 "1996-03-15")))))

(deftest sex-matches?-test
  (testing "case-insensitive match"
    (is (policy/sex-matches? "f" "F"))
    (is (policy/sex-matches? "F" "f"))
    (is (policy/sex-matches? "m" "M")))
  (testing "mismatch"
    (is (not (policy/sex-matches? "f" "m")))))
