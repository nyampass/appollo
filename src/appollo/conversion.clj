(ns appollo.conversion
  (use conceit.commons)
  (require appollo.conversion.conversion-exception)
  (import appollo.conversion.conversion-exception))

(defmulti* type-converters (fn [context] context)
  :methods [(:default [context] (fn [type value] value))])

(defmulti* type? (fn [type value] type)
  :methods [(:default [type value] false)
            (nil [type value] true)
            (:string [type value] (string? value))
            (:bool [type value] (or (true? value) (false? value)))
            (:integer [type value] (integer? value))
            (:float [type value] (float? value))])

(defmulti* apply-rule (fn [rule options source field] rule)
  :methods [(:default [rule options source field]
                      {:converted source})
            (:convert [rule f source field]
                      (f source field))
            (:validate [rule pred-and-options source field]
                       (let [[pred & {:keys [error]}] (if (and (coll? pred-and-options) (not (map? pred-and-options)) (not (set? pred-and-options))) pred-and-options [pred-and-options])]
                         {:error (if (pred (get source field)) nil (or error :invalid-value))
                          :converted source}))
            (:range [rule {:keys [min max] :as options} source field]
                    (apply-rule :validate (concat [#(in-range? % :min min :max max)]
                                                  (apply concat (merge {:error :out-of-range} options))) source field))
            (:length-range [rule {:keys [min max] :as options} source field]
                           (apply-rule :validate (concat [#(in-range? (count %) :min min :max max)]
                                                         (apply concat (merge {:error :invalid-length} options))) source field))
            (:apply [rule f source field]
                    {:converted (assoc source field (f (get source field)))})])

(defn with-conversion-context [values context]
  (with-meta values {::conversion-context context}))

(defn- conversion-context [values]
  (::conversion-context (meta values)))

(defn- convert-field [source field rule]
  (let [rule-pairs (partition 2 rule)
        rule-map (map-from-pairs rule-pairs)]
    (if-not (contains? source field)
      (if (:optional rule-map)
        {:converted source}
        {:error :unspecified
         :converted source})
      (let [value ((type-converters (conversion-context source)) (:type rule-map) (get source field))]
        (if-not (type? (:type rule-map) value)
          {:error :invalid-type
           :converted (assoc source field value)}
          (loop [converted (assoc source field value) rest-pairs rule-pairs]
            (if (empty? rest-pairs)
              {:converted converted}
              (let [pair (first rest-pairs)
                    {:keys [error subs converted] :as result} (apply-rule (first pair) (second pair) converted field)]
                (if (or error subs)
                  result
                  (recur converted (rest rest-pairs)))))))))))

(defn- convert-fields [source rules]
  (let [context (conversion-context source)]
    (reduce (fn [{:keys [errors converted] :as result} [field rule]]
              (let [{:keys [error subs converted] :as field-result} (convert-field (?-> converted (not (conversion-context converted)) (with-conversion-context context)) field rule)]
                (?-> (assoc result :converted converted)
                     (or error subs) (assoc-in [:errors field] (dissoc field-result :converted))
                     error (assoc-in [:errors field :rule] rule))))
            {:converted source}
            rules)))

(defn collect-errors [result]
  (reduce (fn [errors [field field-result]]
            (let [sub-errors (if (:subs field-result) (collect-errors (:subs field-result)) {})]
              (if (or (:error field-result) (not (empty? sub-errors)))
                (assoc errors
                  field (?-> (dissoc field-result :error :subs)
                             (:error field-result) (assoc :error (:error field-result))
                             (not (empty? sub-errors)) (assoc :subs sub-errors)))
                errors)))
          {}
          result))

(defn convert [source {:as rules}]
  (let [filtered (with-conversion-context (reduce (fn [filtered field] (?-> filtered (contains? source field) (assoc field (get source field))))
                                                  {}
                                                  (keys rules))
                   (conversion-context source))
        result (convert-fields filtered rules)
        errors (collect-errors (:errors result))]
    (if (empty? errors)
      (:converted result)
      (throw (new conversion-exception errors)))))

(defmethod apply-rule :every-sub [rule sub-rules source field]
  (let [subs (get source field)
        {:keys [errors converted]} (convert-fields (?-> subs (not (conversion-context subs)) (with-conversion-context (conversion-context source)))
                                                   (map-to-map (fn [key] [key sub-rules]) (keys subs)))]
    (?-> {:converted (assoc source field converted)}
         (not (empty? errors)) (assoc :subs errors))))
