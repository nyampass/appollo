(ns appollo.apps
  (use conceit.commons
       conceit.commons.mongodb)
  (require [appollo
            [users :as users]]
           [mongoika :as mongo]))

(defn- app-from-mongo-data [mongo-data]
  (merge {:id (str (:_id mongo-data))}
         (dissoc mongo-data :_id)))

(defn apps []
  (mongo/map-after app-from-mongo-data :apps))

(defn by-id [id]
  (fetch-one-by-id id (apps)))

(defn app-with-authentication [id secret]
  (when-let [app (by-id id)]
    (when (= secret (:secret app))
      app)))

(defn users [app]
  (mongo/restrict :app-id (:id app) (users/users)))

(defn user [app user-id-in-app & {:keys [insert]}]
  (or (mongo/fetch-one (mongo/restrict :app-id (:id app) :id-in-app user-id-in-app (users/users)))
      (when insert (mongo/insert! (users/users) {:app-id (:id app)
                                                 :id-in-app user-id-in-app
                                                 :registered-at (now)
                                                 :updated-at (now)}))))

