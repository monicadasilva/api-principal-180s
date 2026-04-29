(ns api-principal.config
  (:gen-class)
  (:require [integrant.core :as ig]
            [aero.core :as aero]
            [clojure.java.io :as io]
            [api-principal.infrastructure.http-server]))

(defn load-config []
  (aero/read-config (io/resource "system.edn")))

(defn -main [& _]
  (println "Starting API Principal...")
  (let [config (load-config)
        _      (ig/load-namespaces config)
        system (ig/init config)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(ig/halt! system)))
    (println "API Principal started.")
    @(promise)))
