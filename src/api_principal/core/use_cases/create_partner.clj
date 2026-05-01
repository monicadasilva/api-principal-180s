(ns api-principal.core.use-cases.create-partner
  (:require [api-principal.core.domain.partner :as partner]))

(defn execute [{:keys [save-partner!]} {:keys [name cnpj]}]
  (if-not (partner/valid-cnpj? cnpj)
    {:status 422 :body {:error "Invalid CNPJ"}}
    (let [p (partner/build name cnpj)]
      (save-partner! p)
      {:status 201 :body p})))
