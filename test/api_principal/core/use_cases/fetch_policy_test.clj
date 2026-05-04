(ns api-principal.core.use-cases.fetch-policy-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-principal.core.use-cases.fetch-policy :as fetch-policy]))

(def ^:private partner-id (random-uuid))
(def ^:private policy-id  (random-uuid))

(defn- repo-with [overrides]
  (merge {:find-policy (fn [_] {:id policy-id :partner-id partner-id})}
         overrides))

(defn- insurer-with [overrides]
  (merge {:get-policy (fn [_] {:status 200 :body {:id (str policy-id)}})}
         overrides))

(deftest policy-not-found-returns-404
  (let [repo    (repo-with {:find-policy (fn [_] nil)})
        res     (fetch-policy/execute repo (insurer-with {}) partner-id policy-id)]
    (is (= 404 (:status res)))
    (is (= "Not found" (-> res :body :error)))))

(deftest policy-belongs-to-other-partner-returns-404
  (let [repo (repo-with {:find-policy (fn [_] {:id policy-id :partner-id (random-uuid)})})
        res  (fetch-policy/execute repo (insurer-with {}) partner-id policy-id)]
    (testing "ownership check prevents cross-partner access"
      (is (= 404 (:status res)))
      (is (= "Not found" (-> res :body :error))))))

(deftest happy-path-delegates-to-insurer
  (let [calls (atom [])
        insurer {:get-policy (fn [id] (swap! calls conj id)
                               {:status 200 :body {:id (str id)}})}
        res   (fetch-policy/execute (repo-with {}) insurer partner-id policy-id)]
    (testing "returns insurer response"
      (is (= 200 (:status res))))
    (testing "calls insurer with correct policy-id"
      (is (= [policy-id] @calls)))))

(deftest insurer-404-is-returned-as-is
  (let [insurer {:get-policy (fn [_] {:status 404 :body {:error "Policy not found"}})}
        res     (fetch-policy/execute (repo-with {}) insurer partner-id policy-id)]
    (is (= 404 (:status res)))))
