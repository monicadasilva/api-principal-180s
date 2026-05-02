(ns api-principal.core.use-cases.create-policy
  (:require [api-principal.core.domain.policy :as policy]
            [taoensso.telemere :as t]))

(defn- persist-or-enqueue! [{:keys [save-policy! enqueue-pending-policy!]} policy]
  (try
    (save-policy! policy)
    (catch Exception save-ex
      (try
        (enqueue-pending-policy! {:policy-id  (:id policy)
                                  :partner-id (:partner-id policy)
                                  :error      (.getMessage save-ex)})
        (t/log! :warn (str "Policy save deferred to retry queue: " (:id policy)))
        (catch Exception enqueue-ex
          (t/error! ::policy-persistence-lost
                    (ex-info "Save and enqueue both failed"
                             {:policy-id     (:id policy)
                              :partner-id    (:partner-id policy)
                              :save-error    (.getMessage save-ex)
                              :enqueue-error (.getMessage enqueue-ex)}
                             enqueue-ex)))))))

(defn execute [{:keys [find-quote] :as repo} {:keys [create-policy!]}
               partner-id quotation-id name gender date-of-birth]
  (let [quote (find-quote quotation-id)]
    (cond
      (nil? quote)
      {:status 404 :body {:error "Quotation not found"}}

      (not (policy/owned-by? quote partner-id))
      {:status 404 :body {:error "Not found"}}

      (policy/quote-expired? quote)
      {:status 422 :body {:error "Quotation has expired"}}

      (not (policy/age-matches-dob? (:age quote) date-of-birth))
      {:status 422 :body {:error "Date of birth does not match age in quotation"}}

      (not (policy/sex-matches? (:gender quote) gender))
      {:status 422 :body {:error "Sex does not match quotation"}}

      :else
      (let [result (create-policy! quotation-id name gender date-of-birth)]
        (when (= 200 (:status result))
          (persist-or-enqueue! repo {:id         (java.util.UUID/fromString (-> result :body :id))
                                     :partner-id partner-id}))
        result))))
