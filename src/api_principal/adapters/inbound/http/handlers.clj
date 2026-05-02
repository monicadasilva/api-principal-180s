(ns api-principal.adapters.inbound.http.handlers
  (:require [api-principal.core.use-cases.create-partner :as create-partner]
            [api-principal.core.use-cases.create-quote   :as create-quote]
            [api-principal.core.use-cases.create-policy  :as create-policy]
            [api-principal.core.use-cases.fetch-policy   :as fetch-policy]))

(defn create-partner [{repo :repo, {:keys [body]} :parameters}]
  (create-partner/execute repo body))

(defn create-quote [{repo :repo, {:keys [path body]} :parameters, insurer :insurer}]
  (create-quote/execute repo insurer (:partner-id path) (:age body) (:sex body)))

(defn create-policy [{repo :repo, insurer :insurer, {:keys [path body]} :parameters}]
  (let [{:keys [quotation_id name sex date_of_birth]} body]
    (create-policy/execute repo insurer (:partner-id path) quotation_id name sex date_of_birth)))

(defn get-policy [{repo :repo, insurer :insurer, {:keys [path]} :parameters}]
  (fetch-policy/execute repo insurer (:partner-id path) (:policy-id path)))
