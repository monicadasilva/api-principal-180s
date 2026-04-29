(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]))

(def lib       'api-principal/api-principal-180s)
(def version   "1.0.0")
(def main      'api-principal.config)
(def class-dir "target/classes")
(def uber-file "target/api-principal.jar")

(defn test [opts]
  (let [basis    (b/create-basis {:aliases [:test]})
        cmds     (b/java-command {:basis     basis
                                  :main      'clojure.main
                                  :main-args ["-m" "cognitect.test-runner"]})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit) (throw (ex-info "Tests failed" {}))))
  opts)

(defn uber [opts]
  (b/delete {:path "target"})
  (b/copy-dir {:src-dirs   ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis       (b/create-basis {})
                  :src-dirs    ["src"]
                  :class-dir   class-dir
                  :ns-compile  [main]})
  (b/uber {:lib       lib
           :main      main
           :uber-file uber-file
           :basis     (b/create-basis {})
           :class-dir class-dir})
  opts)

(defn ci [opts]
  (test opts)
  (uber opts))
