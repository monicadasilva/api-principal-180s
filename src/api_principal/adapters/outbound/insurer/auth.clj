(ns api-principal.adapters.outbound.insurer.auth
  (:require [hato.client :as http]
            [clojure.core.memoize :as memo]
            [taoensso.telemere :as t]))

(defn- fetch-token! [base-url api-key http-client]
  (try
    (or (-> (http/post (str base-url "/api/auth")
                       {:http-client http-client
                        :headers     {"x-api-key" api-key}
                        :as          :json})
            :body
            :access_token)
        (throw (ex-info "Insurer auth response missing access_token" {})))
    (catch Exception e
      (t/error! ::insurer-auth-error e)
      (throw (ex-info "Failed to obtain insurer token"
                      {:type :error/insurer-auth-failed}
                      e)))))

(defn make-token-fn [base-url api-key http-client]
  (memo/ttl #(fetch-token! base-url api-key http-client)
            :ttl/threshold (* 55 60 1000)))
