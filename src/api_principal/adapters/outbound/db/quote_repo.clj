(ns api-principal.adapters.outbound.db.quote-repo
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as sql]))

(defn save-quote! [datasource quote]
  (jdbc/execute-one! datasource
                     (sql/format {:insert-into :quotes
                                  :columns     [:id :partner-id :age :gender :price :expire-at]
                                  :values      [[(:id quote)
                                                 (:partner-id quote)
                                                 (:age quote)
                                                 (:gender quote)
                                                 (:price quote)
                                                 (:expire-at quote)]]})))

(defn find-quote [datasource quote-id]
  (when-let [quote (jdbc/execute-one! datasource
                                      (sql/format {:select [:*]
                                                   :from   [:quotes]
                                                   :where  [:= :id quote-id]})
                                      {:builder-fn rs/as-unqualified-kebab-maps})]
    (update quote :expire-at #(.toLocalDate %))))
