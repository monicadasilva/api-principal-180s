(ns api-principal.infrastructure.http-server
  (:require [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [taoensso.telemere :as log]
            [api-principal.adapters.inbound.http.routes :as routes]))

(def ^:private stop-timeout-ms 30000)

(defmethod ig/init-key :http/server [_ {:keys [port repo insurer]}]
  (log/log! :info (str "Starting HTTP server on port " port))
  (doto (jetty/run-jetty (routes/build {:repo repo :insurer insurer})
                         {:port port :join? false})
    (.setStopTimeout stop-timeout-ms)))

(defmethod ig/halt-key! :http/server [_ server]
  (log/log! :info (str "Stopping HTTP server (draining up to " stop-timeout-ms " ms)"))
  (.stop server))
