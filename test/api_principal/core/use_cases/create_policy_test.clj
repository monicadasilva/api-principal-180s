(ns api-principal.core.use-cases.create-policy-test
  (:require [api-principal.core.use-cases.create-policy :as create-policy]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [taoensso.telemere :as t])
  (:import [java.time LocalDate]))

(use-fixtures :once
  (fn [run-tests]
    (t/with-min-level :fatal (run-tests))))

(def ^:private partner-id (random-uuid))
(def ^:private policy-id  "fd3261bc-9e3c-4d4e-acae-8c4d5dc60b79")
(def ^:private quote-id   (random-uuid))

(defn- valid-quote []
  {:id         quote-id
   :partner-id partner-id
   :age        30
   :gender     "F"
   :expire-at  (LocalDate/of 2099 12 31)})

(defn- ok-insurer
  ([] (ok-insurer policy-id))
  ([id]
   {:create-policy!
    (fn [_q _n _g _d]
      {:status 200 :body {:id id}})}))

(defn- repo-with [overrides]
  (merge {:find-quote              (fn [_] (valid-quote))
          :save-policy!            (fn [_] :ok)
          :enqueue-pending-policy! (fn [_] :ok)}
         overrides))

(deftest quotation-not-found
  (let [repo (repo-with {:find-quote (fn [_] nil)})
        res  (create-policy/execute repo (ok-insurer)
                                    partner-id quote-id "João" "f" "1996-03-15")]
    (is (= 404 (:status res)))
    (is (= "Quotation not found" (-> res :body :error)))))

(deftest quotation-belongs-to-other-partner
  (let [repo (repo-with {:find-quote (fn [_] (assoc (valid-quote)
                                                    :partner-id (random-uuid)))})
        res  (create-policy/execute repo (ok-insurer)
                                    partner-id quote-id "João" "f" "1996-03-15")]
    (is (= 404 (:status res)))))

(deftest expired-quotation
  (let [repo (repo-with {:find-quote (fn [_] (assoc (valid-quote)
                                                    :expire-at (LocalDate/of 2020 1 1)))})
        res  (create-policy/execute repo (ok-insurer)
                                    partner-id quote-id "João" "f" "1996-03-15")]
    (is (= 422 (:status res)))
    (is (= "Quotation has expired" (-> res :body :error)))))

(deftest age-mismatch
  (let [repo (repo-with {})
        res  (create-policy/execute repo (ok-insurer)
                                    partner-id quote-id "João" "f" "1950-01-01")]
    (is (= 422 (:status res)))
    (is (= "Date of birth does not match age in quotation" (-> res :body :error)))))

(deftest sex-mismatch
  (let [repo (repo-with {})
        res  (create-policy/execute repo (ok-insurer)
                                    partner-id quote-id "João" "m" "1996-03-15")]
    (is (= 422 (:status res)))
    (is (= "Sex does not match quotation" (-> res :body :error)))))

(deftest insurer-non-200-passthrough
  (testing "non-200 from insurer is returned as-is and nothing is persisted"
    (let [saves    (atom 0)
          enqueues (atom 0)
          repo     (repo-with {:save-policy!            (fn [_] (swap! saves inc))
                               :enqueue-pending-policy! (fn [_] (swap! enqueues inc))})
          insurer  {:create-policy! (fn [_ _ _ _] {:status 500 :body {:error "boom"}})}
          res      (create-policy/execute repo insurer
                                          partner-id quote-id "João" "f" "1996-03-15")]
      (is (= 500 (:status res)))
      (is (zero? @saves))
      (is (zero? @enqueues)))))

(deftest happy-path-persists-policy
  (let [saved (atom nil)
        repo  (repo-with {:save-policy! (fn [p] (reset! saved p))})
        res   (create-policy/execute repo (ok-insurer)
                                     partner-id quote-id "João" "f" "1996-03-15")]
    (testing "returns insurer 200 and saves policy with UUID id"
      (is (= 200 (:status res)))
      (is (= (java.util.UUID/fromString policy-id) (:id @saved)))
      (is (= partner-id (:partner-id @saved))))))

(deftest save-fails-falls-back-to-enqueue
  (testing "when save throws, policy is enqueued for retry and insurer response still returns"
    (let [enqueued (atom nil)
          repo     (repo-with {:save-policy!            (fn [_] (throw (Exception. "db down")))
                               :enqueue-pending-policy! (fn [p] (reset! enqueued p))})
          res      (create-policy/execute repo (ok-insurer)
                                          partner-id quote-id "João" "f" "1996-03-15")]
      (is (= 200 (:status res)))
      (is (= (java.util.UUID/fromString policy-id) (:policy-id @enqueued)))
      (is (= partner-id (:partner-id @enqueued)))
      (is (= "db down" (:error @enqueued))))))

(deftest save-and-enqueue-both-fail
  (testing "when save and enqueue both throw, error is logged but insurer response still returns"
    (let [repo (repo-with {:save-policy!            (fn [_] (throw (Exception. "save fail")))
                           :enqueue-pending-policy! (fn [_] (throw (Exception. "enqueue fail")))})
          res  (create-policy/execute repo (ok-insurer)
                                      partner-id quote-id "João" "f" "1996-03-15")]
      (is (= 200 (:status res))))))
