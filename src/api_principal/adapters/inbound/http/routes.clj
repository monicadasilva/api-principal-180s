(ns api-principal.adapters.inbound.http.routes
  (:require [api-principal.adapters.inbound.http.handlers :as handlers]
            [api-principal.adapters.inbound.http.middleware :as middleware]
            [clojure.string :as str]
            [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.coercion :as coercion]
            [reitit.coercion.malli :as malli]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [muuntaja.core :as m]))

(defn- ->snake [k]
  (str/replace (name k) "-" "_"))

(def ^:private muuntaja-instance
  (m/create
   (assoc-in m/default-options
             [:formats "application/json" :encoder-opts]
             {:encode-key-fn ->snake})))

(defn build [deps]
  (ring/ring-handler
   (ring/router
    [["/swagger.json"
      {:get {:no-doc  true
             :swagger {:info {:title       "Main API 180 Seguros"
                              :description "API REST that intermediates communication between partners and the insurance company."
                              :version     "1.0.0"}}
             :handler (swagger/create-swagger-handler)}}]

     ["/version"
      {:get {:no-doc  true
             :handler (fn [_] {:status 200 :body {:resp "Main API 180s v1.0"}})}}]

     ["/health"
      {:get {:no-doc  true
             :handler (fn [{:keys [repo]}]
                        (let [db-ok? (try ((:db-health repo)) true (catch Exception _ false))]
                          {:status (if db-ok? 200 503)
                           :body   {:db (if db-ok? "up" "down")}}))}}]

     ["/partners"
      {:swagger {:tags ["partners"]}
       :post    {:summary    "Cadastrar parceiro"
                 :parameters {:body [:map
                                     [:name :string]
                                     [:cnpj [:re #"^\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2}$"]]]}
                 :handler    handlers/create-partner}}]

     ["/partners/:partner-id/quotes"
      {:swagger {:tags ["quotes"]}
       :post    {:summary    "Solicitar cotação"
                 :parameters {:path [:map
                                     [:partner-id :uuid]]
                              :body [:map
                                     [:age [:int {:min 0 :max 99}]]
                                     [:sex [:enum "m" "M" "f" "F" "n" "N"]]]}
                 :handler    handlers/create-quote}}]

     ["/partners/:partner-id/policies"
      {:swagger {:tags ["policies"]}
       :post    {:summary    "Criar apólice"
                 :parameters {:path [:map
                                     [:partner-id :uuid]]
                              :body [:map
                                     [:quotation_id  :uuid]
                                     [:name          :string]
                                     [:sex           [:enum "m" "M" "f" "F" "n" "N"]]
                                     [:date_of_birth [:re #"^\d{4}-\d{2}-\d{2}$"]]]}
                 :handler    handlers/create-policy}}]

     ["/partners/:partner-id/policies/:policy-id"
      {:swagger {:tags ["policies"]}
       :get     {:summary    "Consultar apólice"
                 :parameters {:path [:map
                                     [:partner-id :uuid]
                                     [:policy-id  :uuid]]}
                 :handler    handlers/get-policy}}]]

    {:data {:muuntaja   muuntaja-instance
            :coercion   malli/coercion
            :middleware [swagger/swagger-feature
                         #(middleware/wrap-deps % deps)
                         middleware/wrap-logging
                         muuntaja/format-middleware
                         middleware/exception-middleware
                         coercion/coerce-request-middleware
                         middleware/wrap-partner-auth
                         coercion/coerce-response-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path   "/api-docs"
      :url    "/swagger.json"
      :config {:validatorUrl nil}})
    (ring/create-default-handler))))
