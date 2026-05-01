(ns api-principal.infrastructure.db
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]))

(defmethod ig/init-key :db/pool [_ {:keys [jdbc-url]}]
  (jdbc/get-datasource {:jdbcUrl jdbc-url}))

(defmethod ig/halt-key! :db/pool [_ datasource]
  (when (instance? java.io.Closeable datasource)
    (.close datasource)))
