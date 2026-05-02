(ns api-principal.config
  (:gen-class)
  (:require [integrant.core :as ig]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [taoensso.telemere :as log]
            [api-principal.infrastructure.logging :as logging]
            [api-principal.infrastructure.db]
            [api-principal.infrastructure.migrations]
            [api-principal.infrastructure.repository]
            [api-principal.infrastructure.http-client]
            [api-principal.infrastructure.insurance]
            [api-principal.infrastructure.http-server]
            [api-principal.infrastructure.policy-retry-worker]))

(defn load-config []
  (with-open [r (-> (io/resource "system.edn")
                    io/reader
                    java.io.PushbackReader.)]
    (edn/read
      {:readers {'ig/ref ig/ref
                 'env    #(System/getenv (name %))
                 'long   #(Long/parseLong %)}}
      r)))

(defn -main [& _]
  (logging/setup!)
  (log/log! :info "Starting API Principal...")
  (let [config (load-config)
        _      (ig/load-namespaces config)
        system (ig/init config)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(ig/halt! system)))
    (log/log! :info "API Principal started.")
    @(promise)))
