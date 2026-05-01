(ns api-principal.core.domain.policy
  (:require [clojure.string :as str])
  (:import [java.time LocalDate Period]))

(defn owned-by? [quote partner-id]
  (= partner-id (:partner-id quote)))

(defn quote-expired? [quote]
  (.isBefore (:expire-at quote) (LocalDate/now)))

(defn age-matches-dob?
  "Checks if the age provided in the quote matches the date of birth provided
   in the policy, allowing a 1 year difference to account for potential
   differences in the date of birth and the current date."
  [age date-of-birth]
  (let [dob   (LocalDate/parse date-of-birth)
        years (-> (Period/between dob (LocalDate/now)) .getYears)]
    (<= (Math/abs (- age years)) 1)))

(defn sex-matches? [quote-gender policy-sex]
  (= (str/upper-case quote-gender)
     (str/upper-case policy-sex)))
