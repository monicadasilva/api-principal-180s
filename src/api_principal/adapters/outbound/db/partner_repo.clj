(ns api-principal.adapters.outbound.db.partner-repo
  (:require [next.jdbc :as jdbc]
            [honey.sql :as sql]))

(defn save-partner! [datasource partner]
  (try
    (jdbc/execute-one! datasource
                       (sql/format {:insert-into :partners
                                    :columns     [:id :name :cnpj]
                                    :values      [[(:id partner) (:name partner) (:cnpj partner)]]}))
    (catch org.postgresql.util.PSQLException e
      (if (= "23505" (.getSQLState e))
        (throw (ex-info "cnpj already exists" {:type :error/conflict :field "cnpj"}))
        (throw e)))))
