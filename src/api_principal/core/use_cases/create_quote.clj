(ns api-principal.core.use-cases.create-quote
  (:require [api-principal.core.domain.quote :as quote]))

(defn execute [{:keys [save-quote!]} {:keys [create-quote!]} partner-id age gender]
  (let [result (create-quote! age gender)
        quote  (quote/build partner-id age gender (-> result :body))]
    (save-quote! quote)
    {:status 201 :body (update quote :expire-at str)}))
