(ns appollo.notifications
  (use conceit.commons
       conceit.commons.mongodb)
  (require [appollo
            [cursor :as cursor]
            [conversion :as conversion]
            [apps :as apps]
            [users :as users]
            [services :as services]]
           [mongoika :as mongo]))

(defn- request-from-mongo-data [mongo-data]
  (assoc (merge {:id (str (:_id mongo-data))}
                (dissoc mongo-data :_id))
    :status (keyword (:status mongo-data))
    :type (keyword (:type mongo-data))))

(defn requests []
  (->> :notification-requests
       (mongo/map-after request-from-mongo-data)
       (mongo/order :_id :desc)
       (cursor/as-cursor ::requests)))

(defmethod cursor/only-next ::requests [id requests]
  (mongo/restrict :_id {< id} requests))

(defmethod cursor/only-previous ::requests [id requests]
  (mongo/restrict :_id {> id} requests))

(defn requests-of-app [app]
  (mongo/restrict :app-id (:id app) (requests)))

(defn only-test [requests]
  (mongo/restrict :filter.test true requests))

(defn only-not-test [requests]
  (mongo/restrict :filter.test {:$ne true} requests))

(defn only-status [status requests]
  (mongo/restrict :status status requests))

(defn request-of-app [app id]
  (when-let [request (mongo/fetch-one (mongo/restrict :_id id (requests)))]
    (when (= (:id app) (:app-id request)) request)))

(def request-body-conversion-rule {:message [:type :string
                                             :optional true]
                                   :number [:type :integer
                                            :optional true]
                                   :extend [:optional true
                                            :validate map?
                                            :every-keys [:type :string]]})

(defn- make-new-request [app]
  (let [requested-at (now)]
    {:_id (str (simple-date-format "yyyyMMddHHmmssSSS" requested-at) (:id app) (.nextInt (java.util.Random.) Integer/MAX_VALUE))
     :app-id (:id app)
     :status :pending
     :requested-at requested-at}))

(defn new-request-to-user! [app user body]
  (mongo/insert! (requests)
                 (assoc (make-new-request app)
                   :type :user
                   :user-id (:id-in-app user)
                   :content (conversion/convert body request-body-conversion-rule))))

(def request-filter-conversion-rule {:test [:type :bool
                                            :optional true]})

(defn new-bulk-request! [app filter body]
  (mongo/insert! (requests)
                 (assoc (make-new-request app)
                   :type :all
                   :filter (conversion/convert filter request-filter-conversion-rule)
                   :content (conversion/convert body request-body-conversion-rule))))

(defn- send-to-user! [request app user]
  (loop [services (services/services) errors []]
    (if (empty? services)
      errors
      (let [service (first services)]
        (recur (rest services)
               (try (services/send-to-user! service app user (:content request))
                    errors
                    (catch Exception e
                      (.println System/err (format "Error: request-id: %s app-id: %s user-id: %s service: %s" (:id request) (:id app) (:id-in-app user) service))
                      (.printStackTrace e)
                      (conj errors {:user-id (:id-in-app user)
                                    :message (.getMessage e)}))))))))

(defmulti* send-by-type! (fn [request app] (:type request))
  :methods [(:default [request app] nil)])

(defmethod send-by-type! :user [request app]
  (send-to-user! request app (apps/user app (:user-id request))))

(defmethod send-by-type! :all [request app]
  (loop [users (?-> (users/only-bulk-target (apps/users app))
                    (-> request :filter :test) (users/only-test-target))
         errors []]
    (if (empty? users)
      errors
      (recur (rest users)
             (concat errors (send-to-user! request app (first users)))))))

(defn send! [request]
  (send (agent nil)
        (fn [_]
          (try (mongo/update! :$set {:status :processing}
                              (mongo/restrict :_id (:id request) (requests)))
               (let [errors (try (send-by-type! request (apps/by-id (:app-id request)))
                                 (catch Exception e
                                   (.println System/err (format "Error: request-id: %s app-id: %s" (:id request) (:app-id request)))
                                   (.printStackTrace e)
                                   {:message (.getMessage e)}))]
                 (mongo/update! :$set (?-> {:status (if (empty? errors) :succeeded :failed)
                                            :processed-at (now)}
                                           (not (empty? errors)) (assoc :errors errors))
                                (mongo/restrict :_id (:id request) (requests))))
               (catch Throwable t
                 (.println System/err (format "Error: requeset-id: %s" (:id request)))
                 (.printStackTrace t)
                 (throw t))))))
