(ns api-principal.adapters.outbound.db.pending-policy-repo
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as sql]))

(defn enqueue! [datasource {:keys [policy-id partner-id error]}]
  (jdbc/execute-one! datasource
                     (sql/format {:insert-into :pending-policy-saves
                                  :columns     [:policy-id :partner-id :last-error]
                                  :values      [[policy-id partner-id error]]})))

(defn list-all [datasource]
  (jdbc/execute! datasource
                 (sql/format {:select   [:*]
                              :from     [:pending-policy-saves]
                              :order-by [:created-at]})
                 {:builder-fn rs/as-unqualified-kebab-maps}))

(defn delete! [datasource policy-id]
  (jdbc/execute-one! datasource
                     (sql/format {:delete-from :pending-policy-saves
                                  :where       [:= :policy-id policy-id]})))

(defn record-failure! [datasource policy-id error]
  (jdbc/execute-one! datasource
                     (sql/format {:update :pending-policy-saves
                                  :set    {:attempts        [:+ :attempts 1]
                                           :last-error      error
                                           :last-attempt-at [:now]}
                                  :where  [:= :policy-id policy-id]})))
