(ns api-principal.adapters.inbound.http.routes
  (:require [api-principal.adapters.inbound.http.handlers :as handlers]
            [api-principal.adapters.inbound.http.middleware :as middleware]
            [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.coercion :as coercion]
            [reitit.coercion.malli :as malli]
            [muuntaja.core :as m]))

(defn build [deps]
  (ring/ring-handler
   (ring/router
    [["/version"
      {:get (fn [_] {:status 200 :body {:resp "API Principal 180s v1.0"}})}]
     ["/partners"
      {:post {:parameters {:body [:map
                                  [:name :string]
                                  [:cnpj [:re #"^\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2}$"]]]}
              :handler    handlers/create-partner}}]
     ["/partners/:partner-id/quotes"
      {:post {:parameters {:path [:map
                                 [:partner-id :uuid]]
                           :body [:map
                                  [:age [:int {:min 0 :max 99}]]
                                  [:sex [:enum "m" "M" "f" "F" "n" "N"]]]}
              :handler    handlers/create-quote}}]
     ["/partners/:partner-id/policies"
      {:post {:parameters {:path [:map
                                  [:partner-id :uuid]]
                           :body [:map
                                  [:quotation_id  :uuid]
                                  [:name          :string]
                                  [:sex           [:enum "m" "M" "f" "F" "n" "N"]]
                                  [:date_of_birth [:re #"^\d{4}-\d{2}-\d{2}$"]]]}
              :handler    handlers/create-policy}}]
     ["/partners/:partner-id/policies/:policy-id"
      {:get {:parameters {:path [:map
                                 [:partner-id :uuid]
                                 [:policy-id  :uuid]]}
              :handler    handlers/get-policy}}]]
    {:data {:muuntaja   m/instance
            :coercion   malli/coercion
            :middleware [#(middleware/wrap-deps % deps)
                         middleware/wrap-logging
                         muuntaja/format-middleware
                         middleware/exception-middleware
                         coercion/coerce-request-middleware
                         middleware/wrap-partner-auth
                         coercion/coerce-response-middleware]}})
   (ring/create-default-handler)))
