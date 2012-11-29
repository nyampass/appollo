(ns appollo.users
  (use conceit.commons
       conceit.commons.mongodb)
  (require [appollo
            [cursor :as cursor]
            [conversion :as conversion]
            [services :as services]]
           [mongoika :as mongo]))

(defn- user-from-mongo-data [mongo-data]
  (merge {:id (str (:_id mongo-data))}
         (dissoc mongo-data :_id)))

(defn users []
  (cursor/as-cursor ::users-in-app (mongo/order :id-in-app :asc (mongo/map-after user-from-mongo-data :users))))

(defmethod cursor/only-next ::users-in-app [id-in-app users]
  (mongo/restrict :id-in-app {> id-in-app} users))

(defmethod cursor/only-previous ::users-in-app [id-in-app users]
  (mongo/restrict :id-in-app {< id-in-app} users))

(defn only-bulk-target [users]
  (mongo/restrict :excluded? {:$ne true} users))

(defn only-test-target [users]
  (mongo/restrict :test? true users))

(defn set-service! [user service settings]
  (when (services/enabled? service)
    (update-by-id! :$set {(keyword (str "services." (name service))) (conversion/convert settings (services/user-settings-conversion service))
                          :updated-at (now)}
                   (:id user) (users))))

(defn delete! [user]
  (mongo/delete! (restrict-by-id (:id user) (users))))

(def ^{:private true} user-settings-conversion-rules {:test? [:type :bool
                                                              :optional true]
                                                      :excluded? [:type :bool
                                                                  :optional true]})
(defn set-settings! [user settings]
  (let [settings (conversion/convert settings user-settings-conversion-rules)]
    (update-by-id! :$set (assoc settings :updated-at (now))
                   (:id user) (users))))

