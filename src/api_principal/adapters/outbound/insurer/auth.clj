(ns api-principal.adapters.outbound.insurer.auth
  (:require [hato.client :as http]
            [clojure.core.memoize :as memo]))

(defn- fetch-token! [base-url api-key http-client]
  (-> (http/post (str base-url "/api/auth")
                 {:http-client http-client
                  :headers     {"x-api-key" api-key}
                  :as          :json})
      :body
      :access_token))

(defn make-token-fn [base-url api-key http-client]
  (memo/ttl #(fetch-token! base-url api-key http-client)
            :ttl/threshold (* 55 60 1000)))
