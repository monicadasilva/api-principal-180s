(ns api-principal.infrastructure.logging
  (:require [taoensso.telemere :as t]))

(defn setup! []
  (t/set-min-level! :info))
