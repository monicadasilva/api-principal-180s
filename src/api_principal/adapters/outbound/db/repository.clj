(ns api-principal.adapters.outbound.db.repository
  (:require [api-principal.adapters.outbound.db.partner-repo :as partner-repo]
            [api-principal.adapters.outbound.db.quote-repo   :as quote-repo]
            [api-principal.adapters.outbound.db.policy-repo  :as policy-repo]))

(defn make [datasource]
  {:save-partner! #(partner-repo/save-partner! datasource %)
   :save-quote!   #(quote-repo/save-quote! datasource %)
   :find-quote    #(quote-repo/find-quote datasource %)
   :save-policy!  #(policy-repo/save-policy! datasource %)})
