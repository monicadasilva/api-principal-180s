(ns api-principal.adapters.outbound.db.quote-repo-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [api-principal.adapters.outbound.db.quote-repo :as repo]
            [api-principal.adapters.outbound.db.partner-repo :as partner-repo]
            [api-principal.core.domain.api-key :as api-key]
            [api-principal.integration.fixtures :as f])
  (:import [java.time LocalDate]))

(use-fixtures :each f/clean-db)

(defn- insert-partner! []
  (let [partner-id (random-uuid)]
    (partner-repo/save-partner! @f/datasource
                                {:id           partner-id
                                 :name         "Acme"
                                 :cnpj         "12345678000195"
                                 :api-key-hash (api-key/digest (random-uuid))})
    partner-id))

(defn- new-quote [partner-id]
  {:id         (random-uuid)
   :partner-id partner-id
   :age        30
   :gender     "F"
   :price      "500.0"
   :expire-at  (LocalDate/of 2099 12 31)})

(deftest save-and-find-quote
  (let [q (new-quote (insert-partner!))]
    (repo/save-quote! @f/datasource q)
    (testing "find-quote returns the saved quote"
      (let [found (repo/find-quote @f/datasource (:id q))]
        (is (= (:id q)         (:id found)))
        (is (= (:partner-id q) (:partner-id found)))
        (is (= (:age q)        (:age found)))
        (is (= (:gender q)     (:gender found)))
        (is (= (:price q)      (:price found)))))))

(deftest find-quote-parses-expire-at-to-local-date
  (let [q (new-quote (insert-partner!))]
    (repo/save-quote! @f/datasource q)
    (let [found (repo/find-quote @f/datasource (:id q))]
      (testing "expire-at is a LocalDate"
        (is (instance? LocalDate (:expire-at found))))
      (testing "expire-at value is correct"
        (is (= (LocalDate/of 2099 12 31) (:expire-at found)))))))

(deftest find-quote-returns-nil-when-not-found
  (is (nil? (repo/find-quote @f/datasource (random-uuid)))))
