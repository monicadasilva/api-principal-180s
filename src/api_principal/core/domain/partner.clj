(ns api-principal.core.domain.partner
  (:require [clojure.string :as str]
            [api-principal.core.domain.api-key :as api-key]))

(defn- cnpj-digit [digits weights]
  (let [remainder (mod (reduce + (map * digits weights)) 11)]
    (if (< remainder 2) 0 (- 11 remainder))))

(defn valid-cnpj? [cnpj]
  (let [digits (mapv #(Character/getNumericValue %) (str/replace cnpj #"\D" ""))]
    (and (= 14 (count digits))
         (= (nth digits 12) (cnpj-digit (subvec digits 0 12) [5 4 3 2 9 8 7 6 5 4 3 2]))
         (= (nth digits 13) (cnpj-digit (subvec digits 0 13) [6 5 4 3 2 9 8 7 6 5 4 3 2])))))

(defn build [name cnpj]
  (let [token (random-uuid)]
    {:id           (random-uuid)
     :name         name
     :cnpj         (str/replace cnpj #"\D" "")
     :api-key      token
     :api-key-hash (api-key/digest token)}))
