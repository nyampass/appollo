(ns appollo.web.request
  (use conceit.commons)
  (require [appollo
            [conversion :as conversion]]))

(defn structured-params [params prefix]
  (let [prefix-str (str (name prefix) ".")]
    (reduce (fn [result [field value]]
              (?-> result
                   (.startsWith (name field) prefix-str) (assoc (keyword (.substring (name field) (count prefix-str))) value)))
            {}
            params)))

(defmulti* convert-type-for-conversion (fn [type value] type)
  :methods [(:default [type value] value)
            (:string [type value] (str value))
            (:bool [type value] (cond (.equalsIgnoreCase "true" value) true
                                      (.equalsIgnoreCase "false" value) false
                                      :else value))
            (:integer [type value] (int-from value))
            (:float [type value] (double-from value))])

(def conversion-context ::conversion-context)

(defmethod conversion/type-converters conversion-context [context]
  convert-type-for-conversion)

(defn with-conversion-context [values]
  (conversion/with-conversion-context values conversion-context))
