(ns api-principal.adapters.inbound.http.middleware
  (:require [clojure.string :as str]
            [malli.core :as m]
            [taoensso.telemere :as t]
            [reitit.ring.middleware.exception :as exception]
            [api-principal.core.domain.api-key :as api-key]))

(defn wrap-deps [handler deps]
  (fn [request]
    (handler (merge request deps))))

(defn wrap-logging [handler]
  (fn [{:keys [request-method uri] :as request}]
    (let [response (handler request)]
      (t/log! :info (str (str/upper-case (name request-method)) " " uri " -> " (:status response)))
      response)))

(defn wrap-partner-auth [handler]
  (fn [{:keys [repo] {:keys [path]} :parameters :as request}]
    (if-let [partner-id (:partner-id path)]
      (let [token   (some-> (get-in request [:headers "authorization"])
                            (str/replace #"(?i)^bearer\s+" ""))
            partner (when token ((:find-partner repo) partner-id))]
        (if (and partner (= (api-key/digest token) (:api-key-hash partner)))
          (handler request)
          {:status 401 :body {:error "Unauthorized"}}))
      (handler request))))

(defn ^:private schema->type [key schema]
  (let [schema-type (m/type schema)]
    (cond
      (and (keyword? schema-type) (= key :cnpj)) "Invalid CNPJ"
      (and (keyword? schema-type) (= key :age))  "Invalid age, must be an integer between 0 and 99"
      (and (keyword? schema-type) (= key :sex))  "Invalid sex, must be one of 'm/M', 'f/F', 'n/N'"
      (sequential? schema-type)                  (name (m/type (first schema)))
      (keyword? schema-type)                     (name schema-type)
      :else                                      "Unknown")))

(defn ^:private problem->entry [{:keys [path schema value]}]
  (let [key (last path)]
    [(name key)
     (if (nil? value) "required" (schema->type key schema))]))

(defn ^:private coercion-error-handler [exception _request]
  (let [details (->> (-> exception ex-data :errors)
                     (map problem->entry)
                     (into {}))]
    {:status 400
     :body   {:error "Type coercion error" :details details}}))

(defn ^:private conflict-error-handler [exception _request]
  {:status 409
   :body   {:error  "Conflict"
            :detail (str/capitalize (ex-message exception))}})

(defn ^:private default-error-handler [exception request]
  (t/error! ::unhandled-error exception)
  (t/log! :error (str "Unhandled error on " (:uri request)))
  {:status 500
   :body   {:error "Internal error, please contact support."}})

(def exception-middleware
  (exception/create-exception-middleware
   {:reitit.coercion/request-coercion coercion-error-handler
    :error/conflict                   conflict-error-handler
    ::exception/default               default-error-handler}))
