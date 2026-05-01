(ns api-principal.infrastructure.migrations
  (:require [integrant.core :as ig]
            [ragtime.jdbc :as ragtime-jdbc]
            [ragtime.core :as ragtime]
            [taoensso.telemere :as t]))

(defmethod ig/init-key :db/migrations [_ {:keys [datasource]}]
  (t/log! :info "Running database migrations...")
  (let [store      (ragtime-jdbc/sql-database {:datasource datasource})
        migrations (ragtime-jdbc/load-resources "migrations")]
    (ragtime/migrate-all store {} migrations))
  (t/log! :info "Database migrations complete.")
  datasource)

(defmethod ig/halt-key! :db/migrations [_ _])
