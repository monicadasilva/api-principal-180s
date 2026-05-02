(ns api-principal.infrastructure.db
  (:require [integrant.core :as ig]
            [next.jdbc.connection :as conn]
            [taoensso.telemere :as t])
  (:import (com.zaxxer.hikari HikariDataSource)))

(defmethod ig/init-key :db/pool [_ {:keys [jdbc-url pool-size]
                                    :or   {pool-size 10}}]
  (t/log! :info (str "Initializing HikariCP pool (size " pool-size ")"))
  (conn/->pool HikariDataSource
               {:jdbcUrl            jdbc-url
                :maximumPoolSize    pool-size
                :minimumIdle        (max 1 (quot pool-size 5))
                :connectionTimeout  30000
                :idleTimeout        600000
                :maxLifetime        1800000}))

(defmethod ig/halt-key! :db/pool [_ ^HikariDataSource ds]
  (t/log! :info "Closing HikariCP pool")
  (.close ds))
