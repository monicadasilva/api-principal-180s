(ns api-principal.infrastructure.insurance
  (:require [integrant.core :as ig]
            [api-principal.adapters.outbound.insurer.client :as insurer]))

(defmethod ig/init-key :adapter/insurance [_ {:keys [base-url api-key http-client]}]
  (insurer/make base-url api-key http-client))
