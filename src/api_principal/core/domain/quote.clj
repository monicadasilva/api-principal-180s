(ns api-principal.core.domain.quote
  (:require [clojure.string :as str]))

(defn build [partner-id age gender insurer-response]
  {:id        (java.util.UUID/fromString (:id insurer-response))
   :partner-id partner-id
   :age        age
   :gender     (str/upper-case gender)
   :price      (str (:price insurer-response))
   :expire-at  (java.time.LocalDate/parse (:expire_at insurer-response))})
