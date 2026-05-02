(ns api-principal.infrastructure.logging
  (:require [taoensso.telemere :as t]
            [jsonista.core :as json]))

(defn- signal->json [{:keys [level inst ns line msg_ id data error]}]
  (json/write-value-as-string
   (cond-> {:level (some-> level name)
            :time  (some-> inst str)
            :ns    (some-> ns str)
            :msg   (some-> msg_ force)}
     line  (assoc :line line)
     id    (assoc :id (str id))
     data  (assoc :data data)
     error (assoc :error {:type    (-> error class .getName)
                          :message (.getMessage ^Throwable error)}))))

(defn setup! []
  (t/set-min-level! :info)
  (t/remove-handler! :default/console)
  (t/add-handler! :console/json
                  (fn [signal] (println (signal->json signal)))))
