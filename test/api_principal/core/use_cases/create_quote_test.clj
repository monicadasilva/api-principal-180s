(ns api-principal.core.use-cases.create-quote-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-principal.core.use-cases.create-quote :as create-quote]))

(def ^:private partner-id (random-uuid))

(defn- ok-insurer []
  {:create-quote!
   (fn [_age _gender]
     {:status 200
      :body   {:id        (str (random-uuid))
               :age       30
               :sex       "f"
               :price     "500.0"
               :expire_at "2099-12-31"}})})

(defn- repo-with [overrides]
  (merge {:save-quote! (fn [_] :ok)} overrides))

(deftest insurer-non-200-is-returned-as-is
  (testing "422 from insurer is passed through without saving"
    (let [saves   (atom 0)
          repo    (repo-with {:save-quote! (fn [_] (swap! saves inc))})
          insurer {:create-quote! (fn [_ _] {:status 422 :body {:error "invalid age"}})}
          res     (create-quote/execute repo insurer partner-id 30 "f")]
      (is (= 422 (:status res)))
      (is (zero? @saves)))))

(deftest insurer-500-is-returned-as-is
  (let [insurer {:create-quote! (fn [_ _] {:status 500 :body {:error "insurer down"}})}
        res     (create-quote/execute (repo-with {}) insurer partner-id 30 "f")]
    (is (= 500 (:status res)))))

(deftest happy-path-returns-201
  (let [res (create-quote/execute (repo-with {}) (ok-insurer) partner-id 30 "f")]
    (is (= 201 (:status res)))))

(deftest happy-path-saves-quote
  (let [saved (atom nil)
        repo  (repo-with {:save-quote! (fn [q] (reset! saved q))})
        _     (create-quote/execute repo (ok-insurer) partner-id 30 "f")]
    (testing "quote is persisted with UUID id"
      (is (instance? java.util.UUID (:id @saved))))
    (testing "partner-id is set"
      (is (= partner-id (:partner-id @saved))))
    (testing "gender is uppercased"
      (is (= "F" (:gender @saved))))))

(deftest response-expire-at-is-string
  (let [res (create-quote/execute (repo-with {}) (ok-insurer) partner-id 30 "f")]
    (testing "expire-at in response body is a string for JSON serialization"
      (is (string? (-> res :body :expire-at))))))
