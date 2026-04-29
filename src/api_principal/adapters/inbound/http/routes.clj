(ns api-principal.adapters.inbound.http.routes
  (:require [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [muuntaja.core :as m]))

(defn build []
  (ring/ring-handler
    (ring/router
      [["/health" {:get (fn [_] {:status 200 :body {:status "ok"}})}]]
      {:data {:muuntaja   m/instance
              :middleware [muuntaja/format-middleware]}})
    (ring/create-default-handler)))
