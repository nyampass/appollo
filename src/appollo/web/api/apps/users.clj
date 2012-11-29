(ns appollo.web.api.apps.users
  (use conceit.commons
       [compojure.core :only [defroutes context GET POST]])
  (require [appollo
            [pager :as pager]
            [conversion :as conversion]
            [apps :as apps]
            [users :as users]
            [notifications :as notifications]
            [services :as services]]
           [appollo.web
            [request :as web.request]]
           [appollo.web.api
            [response :as api.response]]
           [appollo.web.api.response
            [user :as api.response.user]
            [notification :as api.response.notification]]))

(defn user-unregistered [user-id]
  (api.response/error 404 :users.unregistered (format "User %s has not been registered." user-id)))

(defmacro with-user [[user app user-id & options] & body]
  `(let [app# ~app
         user-id# ~user-id]
     (if-let [~user (apps/user app# user-id# ~@options)]
       (do ~@body)
       (user-unregistered user-id#))))

(defmacro with-user-by-req [[user req & {:keys [user-id] :as options}] & body]
  `(let [req# ~req]
     (with-user [~user (:authenticated-app req#) (or ~user-id (-> req# :params :user-id)) ~@(apply concat (dissoc options :user-id))]
       ~@body)))

(defn unknown-service [service]
  (api.response/error 404 :services.unknown (format "Unknown service: %s" service)))

(defmacro with-service [[service-var service] & body]
  `(let [service# ~service
         service-keyword# (keyword service#)]
     (if (services/enabled? service-keyword#)
       (let [~service-var service-keyword#]
         ~@body)
       (unknown-service service#))))

(defn get-users [{params :params :as req}]
  (let [filter (conversion/convert (web.request/with-conversion-context (web.request/structured-params params :filter))
                                   {:test [:type :bool
                                           :optional true]})
        cursor (conversion/convert (web.request/with-conversion-context (web.request/structured-params params :cursor))
                                   {:previous [:type :string
                                               :optional true]
                                    :next [:type :string
                                           :optional true]
                                    :count [:type :integer
                                            :optional true
                                            :range {:min 1 :max 200}]})
        users (?-> (apps/users (:authenticated-app req))
                   (:test filter) (users/only-test-target))
        pager (pager/make-pager users :count-per-page (or (:count cursor) 50))
        current-result (cond (:previous cursor) (pager/fetch-previous pager (:previous cursor))
                             (:next cursor) (pager/fetch-next pager (:next cursor))
                             :else (pager/fetch pager))]
    {:body {:status "succeeded"
            :users {:count (count users)
                    :data (map api.response.user/make-response current-result)
                    :page {:next (let [last (last current-result)]
                                   (when (and last (pager/next? pager (:id-in-app last))) (:id-in-app last)))
                           :previous (let [first (first current-result)]
                                       (when (and first (pager/previous? pager (:id-in-app first))) (:id-in-app first)))}}}}))

(defn set-service [{{:keys [service] :as params} :params :as req}]
  (with-service [service service]
    (with-user-by-req [user req :insert true]
      {:body {:status (if (boolean (users/set-service! user service (web.request/with-conversion-context params))) :succeeded :failed)}})))

(defn get-user [req]
  (with-user-by-req [user req]
    {:body {:status "succeeded"
            :user (api.response.user/make-response user)}}))

(defn delete-user [req]
  (with-user-by-req [user req]
    (users/delete! user)
    {:body {:status "succeeded"}}))

(defn set-user-settings [{{:keys [user-id test excluded] :as params} :params :as req}]
  (with-user-by-req [user req]
    (let [values (web.request/with-conversion-context (?-> {}
                                                           test (assoc :test? test)
                                                           excluded (assoc :excluded? excluded)))]
      {:body {:status :succeeded
              :user (api.response.user/make-response (users/set-settings! user values))}})))

(defn send-notification [{{:keys [message number] :as params} :params :as req}]
  (with-user-by-req [user req]
    (let [app (:authenticated-app req)
          extend (web.request/structured-params params :extend)
          request (notifications/new-request-to-user! app user (web.request/with-conversion-context (?-> (filter-map-by-key #{:message :number} params)
                                                                                                         (not-empty extend) (assoc :extend extend))))]
      (notifications/send! request)
      {:body {:status :succeeded
              :request (api.response.notification/make-response request)}})))

(defroutes routes
  (GET "/" [] get-users)
  (POST "/:user-id/services/:service" [] set-service)
  (GET "/:user-id" [] get-user)
  (POST "/:user-id/delete" [] delete-user)
  (POST "/:user-id" [] set-user-settings)
  (POST "/:user-id/send" [] send-notification))

(def dispatch
  routes)
