(ns api-principal.core.domain.quote-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-principal.core.domain.quote :as quote])
  (:import [java.time LocalDate]))

(def ^:private partner-id (random-uuid))

(defn- insurer-response [id]
  {:id        id
   :age       30
   :sex       "f"
   :price     "500.0"
   :expire_at "2099-12-31"})

(deftest build-sets-uuid-id
  (let [id  (str (random-uuid))
        q   (quote/build partner-id 30 "f" (insurer-response id))]
    (testing "converts string id to UUID"
      (is (instance? java.util.UUID (:id q)))
      (is (= (java.util.UUID/fromString id) (:id q))))))

(deftest build-uppercases-gender
  (testing "lowercase gender is uppercased"
    (is (= "F" (:gender (quote/build partner-id 30 "f" (insurer-response (str (random-uuid))))))))
  (testing "uppercase gender is preserved"
    (is (= "M" (:gender (quote/build partner-id 30 "M" (insurer-response (str (random-uuid)))))))))

(deftest build-parses-expire-at
  (let [q (quote/build partner-id 30 "f" (insurer-response (str (random-uuid))))]
    (testing "expire_at string is parsed to LocalDate"
      (is (instance? LocalDate (:expire-at q)))
      (is (= (LocalDate/of 2099 12 31) (:expire-at q))))))

(deftest build-preserves-fields
  (let [q (quote/build partner-id 30 "f" (insurer-response (str (random-uuid))))]
    (testing "partner-id, age and price are preserved"
      (is (= partner-id (:partner-id q)))
      (is (= 30 (:age q)))
      (is (= "500.0" (:price q))))))
