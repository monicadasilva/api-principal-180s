(ns api-principal.core.use-cases.fetch-policy)

(defn execute [{:keys [find-policy]} {:keys [get-policy]} partner-id policy-id]
  (let [policy (find-policy policy-id)]
    (if (or (nil? policy) (not= partner-id (:partner-id policy)))
      {:status 404 :body {:error "Not found"}}
      (get-policy policy-id))))
