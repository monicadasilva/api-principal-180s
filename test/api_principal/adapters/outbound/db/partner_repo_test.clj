(ns api-principal.adapters.outbound.db.partner-repo-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [api-principal.adapters.outbound.db.partner-repo :as repo]
            [api-principal.core.domain.api-key :as api-key]
            [api-principal.integration.fixtures :as f]))

(use-fixtures :each f/clean-db)

(defn- new-partner []
  {:id           (random-uuid)
   :name         "Acme"
   :cnpj         "12345678000195"
   :api-key-hash (api-key/digest (random-uuid))})

(deftest save-and-find-partner
  (let [p (new-partner)]
    (repo/save-partner! @f/datasource p)
    (testing "find-partner returns the saved partner"
      (let [found (repo/find-partner @f/datasource (:id p))]
        (is (= (:id p)           (:id found)))
        (is (= (:name p)         (:name found)))
        (is (= (:cnpj p)         (:cnpj found)))
        (is (= (:api-key-hash p) (:api-key-hash found)))))))

(deftest find-partner-returns-nil-when-not-found
  (is (nil? (repo/find-partner @f/datasource (random-uuid)))))

(deftest save-partner-duplicate-cnpj-throws-conflict
  (let [p (new-partner)
        q (assoc p :id (random-uuid) :api-key-hash (api-key/digest (random-uuid)))]
    (repo/save-partner! @f/datasource p)
    (testing "duplicate CNPJ throws ex-info with :type :error/conflict"
      (let [ex (try (repo/save-partner! @f/datasource q) nil
                    (catch clojure.lang.ExceptionInfo e e))]
        (is (some? ex))
        (is (= :error/conflict (-> ex ex-data :type)))))))
