(ns api-principal.core.domain.api-key
  (:import (java.security MessageDigest)))

(defn digest [token]
  (let [bytes (-> (MessageDigest/getInstance "SHA-256")
                  (.digest (.getBytes (str token) "UTF-8")))]
    (apply str (map #(format "%02x" %) bytes))))
