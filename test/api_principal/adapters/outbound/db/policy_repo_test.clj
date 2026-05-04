(ns api-principal.adapters.outbound.db.policy-repo-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [api-principal.adapters.outbound.db.policy-repo :as repo]
            [api-principal.adapters.outbound.db.partner-repo :as partner-repo]
            [api-principal.core.domain.api-key :as api-key]
            [api-principal.integration.fixtures :as f]))

(use-fixtures :each f/clean-db)

(defn- insert-partner! []
  (let [partner-id (random-uuid)]
    (partner-repo/save-partner! @f/datasource
                                {:id           partner-id
                                 :name         "Acme"
                                 :cnpj         "12345678000195"
                                 :api-key-hash (api-key/digest (random-uuid))})
    partner-id))

(defn- new-policy [partner-id]
  {:id         (random-uuid)
   :partner-id partner-id})

(deftest save-and-find-policy
  (let [p (new-policy (insert-partner!))]
    (repo/save-policy! @f/datasource p)
    (testing "find-policy returns the saved policy"
      (let [found (repo/find-policy @f/datasource (:id p))]
        (is (= (:id p)         (:id found)))
        (is (= (:partner-id p) (:partner-id found)))))))

(deftest find-policy-returns-nil-when-not-found
  (is (nil? (repo/find-policy @f/datasource (random-uuid)))))

(deftest save-policy-duplicate-id-throws
  (let [p (new-policy (insert-partner!))]
    (repo/save-policy! @f/datasource p)
    (testing "duplicate policy id throws exception"
      (is (thrown? Exception (repo/save-policy! @f/datasource p))))))
