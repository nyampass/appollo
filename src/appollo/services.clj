(ns appollo.services
  (use conceit.commons))

(defonce ^{:private true} services-atom (atom #{}))

(defn services []
  @services-atom)

(defn enabled? [type]
  (@services-atom type))

(def ^{:private true} service-ns-atom (atom nil))

(defn current-service-ns []
  @service-ns-atom)

(defn service-ns [type]
  (swap! services-atom conj type)
  (reset! service-ns-atom type))

(defmulti* send-notification! (fn [service app-settings user notification] service))

(defmacro def-send-notification! [args & body]
  `(defmethod send-notification! (current-service-ns) [service# ~@args] ~@body))

(defn send-to-user! [service app user notification]
  (let [app-settings (get-in app [:services service])]
    (when (and app-settings)
      (send-notification! service
                          (assoc app-settings :id (:id app))
                          user
                          (assoc notification
                            :extend (remove-map-by-key (set (map keyword (:ignore-extends app-settings))) (:extend notification)))))))

(defmulti* user-settings-conversion (fn [service] service)
  :methods [(:default [service] {})])

(defmacro def-user-settings-conversion [& body]
  `(defmethod user-settings-conversion (current-service-ns) [service#] ~@body))
