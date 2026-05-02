(ns api-principal.adapters.outbound.db.repository
  (:require [next.jdbc :as jdbc]
            [api-principal.adapters.outbound.db.partner-repo         :as partner-repo]
            [api-principal.adapters.outbound.db.quote-repo           :as quote-repo]
            [api-principal.adapters.outbound.db.policy-repo          :as policy-repo]
            [api-principal.adapters.outbound.db.pending-policy-repo  :as pending-policy-repo]))

(defn make [datasource]
  {:save-partner!           #(partner-repo/save-partner! datasource %)
   :find-partner            #(partner-repo/find-partner datasource %)
   :save-quote!             #(quote-repo/save-quote! datasource %)
   :find-quote              #(quote-repo/find-quote datasource %)
   :save-policy!            #(policy-repo/save-policy! datasource %)
   :find-policy             #(policy-repo/find-policy datasource %)
   :enqueue-pending-policy! #(pending-policy-repo/enqueue! datasource %)
   :list-pending-policies   #(pending-policy-repo/list-all datasource)
   :delete-pending-policy!  #(pending-policy-repo/delete! datasource %)
   :record-pending-failure! #(pending-policy-repo/record-failure! datasource %1 %2)
   :db-health               #(jdbc/execute-one! datasource ["SELECT 1"])})
