(ns api-principal.config
  (:gen-class)
  (:require [integrant.core :as ig]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [api-principal.infrastructure.db]
            [api-principal.infrastructure.repository]
            [api-principal.infrastructure.http-client]
            [api-principal.infrastructure.insurance]
            [api-principal.infrastructure.http-server]))

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
  (println "Starting API Principal...")
  (let [config (load-config)
        _      (ig/load-namespaces config)
        system (ig/init config)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(ig/halt! system)))
    (println "API Principal started.")
    @(promise)))
