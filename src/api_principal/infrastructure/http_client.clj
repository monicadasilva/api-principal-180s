(ns api-principal.infrastructure.http-client
  (:require [integrant.core :as ig]
            [hato.client :as http]))

(defmethod ig/init-key :http/client [_ _]
  (http/build-http-client {:connect-timeout 5000
                           :redirect-policy :always}))
