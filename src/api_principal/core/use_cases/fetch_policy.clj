(ns api-principal.core.use-cases.fetch-policy
  (:require [api-principal.core.domain.policy :as policy]))

(defn execute [{:keys [find-quote]} {:keys [get-policy]} partner-id policy-id]
  (let [result (get-policy policy-id)]
    (if (not= 200 (:status result))
      result
      (let [quotation-id (java.util.UUID/fromString (-> result :body :quotation_id))
            quote        (find-quote quotation-id)]
        (if (and quote (policy/owned-by? quote partner-id))
          result
          {:status 404 :body {:error "Not found"}})))))
