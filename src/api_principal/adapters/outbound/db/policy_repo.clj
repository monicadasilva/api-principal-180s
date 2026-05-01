(ns api-principal.adapters.outbound.db.policy-repo
  (:require [next.jdbc :as jdbc]
            [honey.sql :as sql]))

(defn save-policy! [datasource policy]
  (jdbc/execute-one! datasource
                     (sql/format {:insert-into :policies
                                  :columns     [:id :partner-id]
                                  :values      [[(:id policy) (:partner-id policy)]]})))
