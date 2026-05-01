(ns api-principal.core.domain.partner
  (:require [clojure.string :as str]))

(defn build [name cnpj]
  {:id   (random-uuid)
   :name name
   :cnpj (str/replace cnpj #"\D" "")})
