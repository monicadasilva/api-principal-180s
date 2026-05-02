(ns api-principal.integration.fixtures
  (:require [next.jdbc :as jdbc]
            [ring.mock.request :as mock]
            [muuntaja.core :as m]
            [ragtime.jdbc :as ragtime-jdbc]
            [ragtime.core :as ragtime]
            [api-principal.adapters.outbound.db.repository :as db]
            [api-principal.adapters.inbound.http.routes :as routes]))

(def test-db-url
  (or (System/getenv "DATABASE_URL")
      "jdbc:postgresql://localhost:5432/api_principal?user=postgres&password=postgres"))

(def datasource
  (delay (jdbc/get-datasource {:jdbcUrl test-db-url})))

(defonce ^:private migrated
  (delay
    (let [store      (ragtime-jdbc/sql-database {:datasource @datasource})
          migrations (ragtime-jdbc/load-resources "migrations")]
      (ragtime/migrate-all store {} migrations))))

(defn make-mock-insurer []
  (let [store (atom {})]
    {:create-quote!
     (fn [age gender]
       {:status 200
        :body   {:id        (str (random-uuid))
                 :age       age
                 :sex       gender
                 :price     "500.0"
                 :expire_at "2099-12-31"}})

     :create-policy!
     (fn [quotation-id name gender date-of-birth]
       (let [policy-id (random-uuid)]
         (swap! store assoc (str policy-id) (str quotation-id))
         {:status 200
          :body   {:id            policy-id
                   :quotation_id  (str quotation-id)
                   :name          name
                   :sex           gender
                   :date_of_birth date-of-birth}}))

     :get-policy
     (fn [policy-id]
       (if-let [quotation-id (get @store (str policy-id))]
         {:status 200 :body {:id (str policy-id) :quotation_id quotation-id}}
         {:status 404 :body {:error "Policy not found"}}))}))

(defn test-app []
  (routes/build {:repo    (db/make @datasource)
                 :insurer (make-mock-insurer)}))

(defn parse-body [response]
  (m/decode "application/json" (:body response)))

(defn json-request [method path body]
  (-> (mock/request method path)
      (mock/json-body body)))

(defn authed-json-request [method path api-key body]
  (-> (mock/request method path)
      (mock/header "authorization" (str "Bearer " api-key))
      (mock/json-body body)))

(defn authed-request [method path api-key]
  (-> (mock/request method path)
      (mock/header "authorization" (str "Bearer " api-key))))

(defn create-partner! [app name cnpj]
  (parse-body (app (json-request :post "/partners" {:name name :cnpj cnpj}))))

(defn clean-db [test-fn]
  @migrated
  (jdbc/execute! @datasource ["DELETE FROM pending_policy_saves"])
  (jdbc/execute! @datasource ["DELETE FROM policies"])
  (jdbc/execute! @datasource ["DELETE FROM quotes"])
  (jdbc/execute! @datasource ["DELETE FROM partners"])
  (test-fn))
