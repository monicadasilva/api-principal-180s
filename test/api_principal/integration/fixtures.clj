(ns api-principal.integration.fixtures
  (:require [next.jdbc :as jdbc]
            [api-principal.adapters.outbound.db.repository :as db]
            [api-principal.adapters.inbound.http.routes :as routes]))

(def test-db-url
  (or (System/getenv "DATABASE_URL")
      "jdbc:postgresql://localhost:5432/api_principal?user=postgres&password=postgres"))

(def datasource
  (delay (jdbc/get-datasource {:jdbcUrl test-db-url})))

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
       (let [policy-id (str (random-uuid))]
         (swap! store assoc policy-id (str quotation-id))
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

(defn clean-db [test-fn]
  (jdbc/execute! @datasource ["DELETE FROM policies"])
  (jdbc/execute! @datasource ["DELETE FROM quotes"])
  (jdbc/execute! @datasource ["DELETE FROM partners"])
  (test-fn))
