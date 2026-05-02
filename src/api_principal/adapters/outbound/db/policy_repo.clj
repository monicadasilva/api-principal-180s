(ns api-principal.adapters.outbound.db.policy-repo
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as sql]))

(defn save-policy! [datasource policy]
  (jdbc/execute-one! datasource
                     (sql/format {:insert-into :policies
                                  :columns     [:id :partner-id]
                                  :values      [[(:id policy) (:partner-id policy)]]})))

(defn find-policy [datasource policy-id]
  (jdbc/execute-one! datasource
                     (sql/format {:select [:*]
                                  :from   [:policies]
                                  :where  [:= :id policy-id]})
                     {:builder-fn rs/as-unqualified-kebab-maps}))
