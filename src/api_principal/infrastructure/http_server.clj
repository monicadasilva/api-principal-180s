(ns api-principal.infrastructure.http-server
  (:require [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [api-principal.adapters.inbound.http.routes :as routes]))

(defmethod ig/init-key :http/server [_ {:keys [port repo insurer]}]
  (println (str "Starting HTTP server on port " port))
  (jetty/run-jetty (routes/build {:repo repo :insurer insurer}) {:port port :join? false}))

(defmethod ig/halt-key! :http/server [_ server]
  (.stop server))
