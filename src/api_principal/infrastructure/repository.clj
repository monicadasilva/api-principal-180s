(ns api-principal.infrastructure.repository
  (:require [integrant.core :as ig]
            [api-principal.adapters.outbound.db.repository :as db]))

(defmethod ig/init-key :adapter/repository [_ {:keys [datasource]}]
  (db/make datasource))
