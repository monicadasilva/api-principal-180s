(ns api-principal.adapters.outbound.db.pending-policy-repo-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc :as jdbc]
            [api-principal.adapters.outbound.db.pending-policy-repo :as repo]
            [api-principal.integration.fixtures :as f]))

(use-fixtures :each f/clean-db)

(defn- new-row []
  {:policy-id  (random-uuid)
   :partner-id (random-uuid)
   :error      "save failed"})

(deftest enqueue!-test
  (let [row (new-row)]
    (repo/enqueue! @f/datasource row)
    (testing "row is persisted with attempts=1 and the provided error"
      (let [[stored] (repo/list-all @f/datasource)]
        (is (= (:policy-id row)  (:policy-id stored)))
        (is (= (:partner-id row) (:partner-id stored)))
        (is (= 1 (:attempts stored)))
        (is (= "save failed" (:last-error stored)))))))

(deftest list-all-test
  (testing "returns rows ordered by created-at ascending"
    (let [first-row  (new-row)
          second-row (new-row)]
      (repo/enqueue! @f/datasource first-row)
      (Thread/sleep 10)
      (repo/enqueue! @f/datasource second-row)
      (let [ids (mapv :policy-id (repo/list-all @f/datasource))]
        (is (= [(:policy-id first-row) (:policy-id second-row)] ids))))))

(deftest delete!-test
  (let [row (new-row)]
    (repo/enqueue! @f/datasource row)
    (repo/delete! @f/datasource (:policy-id row))
    (testing "row is removed"
      (is (empty? (repo/list-all @f/datasource))))))

(deftest record-failure!-test
  (let [row (new-row)]
    (repo/enqueue! @f/datasource row)
    (repo/record-failure! @f/datasource (:policy-id row) "still failing")
    (testing "increments attempts and updates last-error"
      (let [[stored] (repo/list-all @f/datasource)]
        (is (= 2 (:attempts stored)))
        (is (= "still failing" (:last-error stored)))))))

(deftest enqueue!-duplicate-test
  (testing "second enqueue with same policy-id throws (PK violation)"
    (let [row (new-row)]
      (repo/enqueue! @f/datasource row)
      (is (thrown? Exception (repo/enqueue! @f/datasource row))))))
