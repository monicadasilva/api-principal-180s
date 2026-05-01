(ns api-principal.adapters.outbound.db.partner-repo
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as sql]
            [taoensso.telemere :as t]))

(defn save-partner! [datasource partner]
  (try
    (jdbc/execute-one! datasource
                       (sql/format {:insert-into :partners
                                    :columns     [:id :name :cnpj :api-key]
                                    :values      [[(:id partner) (:name partner) (:cnpj partner) (:api-key partner)]]}))
    (catch org.postgresql.util.PSQLException e
      (if (= "23505" (.getSQLState e))
        (throw (ex-info "cnpj already exists" {:type :error/conflict :field "cnpj"}))
        (do (t/error! ::db-error e)
            (throw e))))))

(defn find-partner [datasource partner-id]
  (jdbc/execute-one! datasource
                     (sql/format {:select [:*]
                                  :from   [:partners]
                                  :where  [:= :id partner-id]})
                     {:builder-fn rs/as-unqualified-kebab-maps}))
