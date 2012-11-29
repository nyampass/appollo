(ns appollo.web.api.response
  (use conceit.commons))

(defn error [http-status code message & {:as additionals}]
  {:status http-status
   :body {:status "failure"
          :error (merge {:code code
                         :message message}
                        additionals)}})

(defn internal-error [exception]
  (error 500 :server.unknown-error "An unknown server error has occurred."))

(defn not-found [code message]
  (error 404 code message))

(defn invalid-parameters [error-parameters]
  (error 403 :parameters.invalid "Invalid parameters."
         :parameters (map (fn [[field {:keys [error rule]}]]
                            {:name field
                             :required (filter-map-by-key #{:type :optional :range} (map-from-pairs (partition 2 rule)))
                             :error error})
                          error-parameters)))
