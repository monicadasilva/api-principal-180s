(ns api-principal.core.use-cases.create-partner
  (:require [api-principal.core.domain.partner :as partner]))

(defn execute [{:keys [save-partner!]} {:keys [name cnpj]}]
  (let [partner (partner/build name cnpj)]
    (save-partner! partner)
    {:status 201 :body partner}))
