(ns appollo.test.conversion
  (use appollo.conversion
       conceit.commons.test
       clojure.test
       conceit.commons)
  (require appollo.conversion.conversion-exception)
  (import appollo.conversion.conversion-exception))

(deftest* convert-test
  (= {:name "Taro" :age 24} (convert {:name "Taro" :age 24}
                                     {:name [:type :string]
                                      :age [:type :integer]}))
  (= {:name "Taro" :age 24 :city "Tokyo"} (convert {:name "Taro" :age 24 :city "Tokyo"}
                                                   {:name [:type :string]
                                                    :age [:type :integer]
                                                    :city [:type :string]})))

(deftest* convert-optional-test
  (= {:name "Jack" :age 18} (convert {:name "Jack" :age 18}
                                     {:name [:type :string]
                                      :age [:type :integer]
                                      :city [:type :string :optional true]}))
  (= {:name "Jack" :age 21 :city "Nagoya"} (convert {:name "Jack" :age 21 :city "Nagoya"}
                                                    {:name [:type :string]
                                                     :age [:type :integer]
                                                     :city [:type :string :optional true]}))
  (= {:city {:error :unspecified
             :rule [:type :string]}} (try (convert {:name "Jack" :age 20}
                                                   {:name [:type :string]
                                                    :age [:type :integer]
                                                    :city [:type :string]})
                                          (catch conversion-exception expected
                                            (.error expected)))))

(deftest* convert-type-test
  (= {:x "abc"} (convert {:x "abc"} {:x {:type :string}}))
  (= {:x {:error :invalid-type
          :rule [:type :string]}} (try (convert {:x 10} {:x [:type :string]})
                                       (catch conversion-exception expected
                                         (.error expected))))
  (= {:x true} (convert {:x true} {:x [:type :bool]}))
  (= {:x false} (convert {:x false} {:x [:type :bool]}))
  (= {:x {:error :invalid-type
          :rule [:type :bool]}} (try (convert {:x 10} {:x [:type :bool]})
                                     (catch conversion-exception expected
                                       (.error expected))))
  (= {:x 200} (convert {:x 200} {:x [:type :integer]}))
  (= {:x {:error :invalid-type
          :rule [:type :integer]}} (try (convert {:x "foo"} {:x [:type :integer]})
                                        (catch conversion-exception expected
                                          (.error expected))))
  (= {:x 3.5} (convert {:x 3.5} {:x [:type :float]}))
  (= {:x {:error :invalid-type
          :rule [:type :float]}} (try (convert {:x "foo"} {:x [:type :float]})
                                      (catch conversion-exception expected
                                        (.error expected))))
  (= {:x {:error :invalid-type
          :rule [:type :xyz]}} (try (convert {:x "foo"} {:x [:type :xyz]})
                                    (catch conversion-exception expected
                                      (.error expected))))
  (= {:x "foo"} (convert {:x "foo"} {:x []})))

(defmethod appollo.conversion/type-converters ::type-convert-test [source-context]
  (fn [type value]
    ((or ({:string str
           :bool boolean
           :integer int-from
           :float double-from} type)
         identity) value)))

(deftest* convert-type-convert-test
  (= {:s "100" :b true :i 20 :f 4.5} (convert (with-conversion-context {:s 100 :b "OK" :i "20" :f "4.5"} ::type-convert-test)
                                              {:s [:type :string]
                                               :b [:type :bool]
                                               :i [:type :integer]
                                               :f [:type :float]}))
  (= {:s "" :b false} (convert (with-conversion-context {:s nil :b nil} ::type-convert-test)
                               {:s [:type :string]
                                :b [:type :bool]})))

(deftest* convert-convert-test
  (= {:x "edcba"} (convert {:x "abcde"}
                           {:x [:convert (fn [values field] {:converted (assoc values field (apply str (reverse (get values field))))})]}))
  (= {:x 300} (convert {:x 1}
                       {:x [:convert (fn [values field] {:converted (assoc values field (+ 2 (get values field)))})
                            :convert (fn [values field] {:converted (assoc values field (* 100 (get values field)))})]}))
  (let [y-rule [:convert (fn [values field] {:error :y-error :converted values})]]
    (= {:y {:error :y-error
            :rule y-rule}} (try (convert {:x "abcde" :y 10}
                                         {:x [:convert (fn [values field] {:converted (assoc values field (apply str (reverse (get values field))))})]
                                          :y y-rule})
                                (catch conversion-exception expected
                                  (.error expected)))))
  (let [x-rule [:convert (fn [values field] {:error :x-error :converted values})]
        y-rule [:convert (fn [values field] {:error :y-error :converted values})]]
    (= {:x {:error :x-error
            :rule x-rule}
        :y {:error :y-error
            :rule y-rule}} (try (convert {:x 1 :y 2}
                                         {:x x-rule
                                          :y y-rule})
                                (catch conversion-exception expected
                                  (.error expected))))))

(deftest* convert-validate-test
  (= {:x 10 :y -30} (convert {:x 10 :y -30}
                             {:x [:validate pos?]
                              :y [:validate neg?]}))
  (= {:x {:error :invalid-value
          :rule [:validate pos?]}} (try (convert {:x -5 :y -20}
                                                 {:x [:validate pos?]
                                                  :y [:validate neg?]})
                                        (catch conversion-exception expected
                                          (.error expected))))
  (= {:x {:error :invalid-value
          :rule [:validate pos?]}
      :y {:error :invalid-value
          :rule [:validate neg?]}} (try (convert {:x -5 :y 10}
                                                 {:x [:validate pos?]
                                                  :y [:validate neg?]})
                                        (catch conversion-exception expected
                                          (.error expected))))
  (= {:x {:error :x-non-positive
          :rule [:validate [pos? :error :x-non-positive]]}
      :y {:error :y-non-positive
          :rule [:validate [neg? :error :y-non-positive]]}} (try (convert {:x -5 :y 10}
                                                                          {:x [:validate [pos? :error :x-non-positive]]
                                                                           :y [:validate [neg? :error :y-non-positive]]})
                                                                 (catch conversion-exception expected
                                                                   (.error expected)))))

(deftest* convert-range-test
  (= {:price 1800} (convert {:price 1800}
                            {:price [:range {:min 0 :max 10000}]}))
  (= {:price {:error :out-of-range
              :rule [:range {:min 0 :max 10000}]}} (try (convert {:price -1000}
                                                                 {:price [:range {:min 0 :max 10000}]})
                                                        (catch conversion-exception expected
                                                          (.error expected))))
  (= {:price {:error :out-of-range
              :rule [:range {:min 0 :max 10000}]}} (try (convert {:price 15000}
                                                                 {:price [:range {:min 0 :max 10000}]})
                                                        (catch conversion-exception expected
                                                          (.error expected))))
  (= {:price {:error :price-range-error
              :rule [:range {:min 1000 :max 5000 :error :price-range-error}]}} (try (convert {:price 100}
                                                                                             {:price [:range {:min 1000 :max 5000 :error :price-range-error}]})
                                                                                    (catch conversion-exception expected
                                                                                      (.error expected)))))

(deftest* convert-length-range-test
  (= {:name "abcde"} (convert {:name "abcde"}
                              {:name [:length-range {:min 3 :max 10}]}))
  (= {:name {:error :invalid-length
             :rule [:length-range {:min 3 :max 10}]}} (try (convert {:name "x"}
                                                                    {:name [:length-range {:min 3 :max 10}]})
                                                           (catch conversion-exception expected
                                                             (.error expected))))
  (= {:name {:error :invalid-length
             :rule [:length-range {:min 2 :max 11}]}} (try (convert {:name "iuawefipahfehuepahemfpxheuifhqioew"}
                                                                    {:name [:length-range {:min 2 :max 11}]})
                                                           (catch conversion-exception expected
                                                             (.error expected))))
  (= {:name {:error :invalid-name-length
             :rule [:length-range {:min 6 :max 8 :error :invalid-name-length}]}} (try (convert {:name "aa"}
                                                                                               {:name [:length-range {:min 6 :max 8 :error :invalid-name-length}]})
                                                                                      (catch conversion-exception expected
                                                                                        (.error expected)))))

(deftest* convert-apply-test
  (= {:name "edcba"} (convert {:name "abcde"}
                              {:name [:apply #(apply str (reverse %))]}))
  (= {:price 1800} (convert {:price 2000}
                            {:price [:apply #(int (* 0.9 %))]})))

(deftest* convert-validate-and-apply-test
  (= {:price 1800} (convert {:price 2000}
                            {:price [:validate pos?
                                     :apply #(int (* 0.9 %))]}))
  (let [price-rule [:validate pos?
                    :apply #(int (* 0.9 %))]]
    (= {:price {:error :invalid-value
                :rule price-rule}} (try (convert {:price -2000}
                                                 {:price price-rule})
                                        (catch conversion-exception expected
                                          (.error expected)))))
  (= {:price 12000} (convert {:price 15000}
                             {:price [:apply #(int (* 0.8 %))
                                      :validate #(> % 10000)]}))
  (let [price-rule [:apply #(int (* 0.8 %))
                    :validate #(> % 10000)]]
    (= {:price {:error :invalid-value
                :rule price-rule}} (try (convert {:price 12000}
                                                 {:price price-rule})
                                        (catch conversion-exception expected
                                          (.error expected))))))

(deftest* convert-every-sub-test
  (= {:attributes {:name "a" :href "foo" :class "bar"}} (convert {:attributes {:name "a" :href "foo" :class "bar"}}
                                                                 {:attributes [:every-sub [:type :string]]}))
  (= {:attributes {:subs {:href {:error :invalid-type
                                 :rule [:type :string]}}}} (try (convert {:attributes {:name "a" :href 10 :class "bar"}}
                                                                         {:attributes [:every-sub [:type :string]]})
                                                                (catch conversion-exception e
                                                                  (.error e))))
  (= {} (convert {}
                 {:attributes [:optional true
                               :every-sub [:type :string]]}))
  (= {:version 10
      :values {:a "foo" :b "bar"}} (convert {:version 10 :values {:a "foo" :b "bar"}}
                                            {:version [:type :integer
                                                       :validate pos?]
                                             :values [:every-sub [:type :string
                                                                  :validate not-empty]]}))
  (= {:values {:subs {:a {:error :invalid-type
                          :rule [:type :string
                                 :validate not-empty]}
                      :b {:error :invalid-value
                          :rule [:type :string
                                 :validate not-empty]}}}} (try (convert {:version 10 :values {:a 10 :b ""}}
                                                                        {:version [:type :integer
                                                                                   :validate pos?]
                                                                         :values [:every-sub [:type :string
                                                                                              :validate not-empty]]})
                                                               (catch conversion-exception e
                                                                 (.error e))))
  (= {:version {:error :negative
                :rule [:type :integer
                       :validate [pos? :error :negative]]}
      :values {:subs {:a {:error :invalid-type
                          :rule [:type :string
                                 :validate not-empty]}}}} (try (convert {:version -4 :values {:a 123 :b "foo"}}
                                                                        {:version [:type :integer
                                                                                   :validate [pos? :error :negative]]
                                                                         :values [:every-sub [:type :string
                                                                                              :validate not-empty]]})
                                                               (catch conversion-exception e
                                                                 (.error e)))))

;; (run-tests)
