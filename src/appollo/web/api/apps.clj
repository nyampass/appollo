(ns appollo.web.api.apps
  (use conceit.commons
       [compojure.core :only [defroutes context GET POST]])
  (require [appollo
            [pager :as pager]
            [apps :as apps]
            [users :as users]
            [conversion :as conversion]
            [notifications :as notifications]]
           [appollo.web
            [request :as web.request]]
           [appollo.web.api
            [response :as api.response]]
           [appollo.web.api.apps
            [users :as api.apps.users]]
           [appollo.web.api.response
            [notification :as api.response.notification]]))

(defn send-notification-to-all [{{:keys [message number] :as params} :params :as req}]
  (let [app (:authenticated-app req)
        filter (web.request/structured-params params :filter)
        extend (web.request/structured-params params :extend)
        request (notifications/new-bulk-request! app
                                                 (web.request/with-conversion-context filter)
                                                 (web.request/with-conversion-context (?-> (filter-map-by-key #{:message :number} params)
                                                                                           (not-empty extend) (assoc :extend extend))))]
    (notifications/send! request)
    {:body {:status :succeeded
            :request (api.response.notification/make-response request)}}))

(defn get-requests [{params :params :as req}]
  (let [filter (conversion/convert (web.request/with-conversion-context (web.request/structured-params params :filter))
                                   {:test [:type :bool
                                           :optional true]
                                    :status [:type :string
                                             :optional true
                                             :apply keyword
                                             :validate #{:succeeded :failed :pending :processing}]})
        cursor (conversion/convert (web.request/with-conversion-context (web.request/structured-params params :cursor))
                                   {:previous [:type :string
                                               :optional true]
                                    :next [:type :string
                                           :optional true]
                                    :count [:type :integer
                                            :optional true
                                            :range {:min 1 :max 200}]})
        requests (?->> (notifications/requests-of-app (:authenticated-app req))
                       (true? (:test filter)) (notifications/only-test)
                       (false? (:test filter)) (notifications/only-not-test)
                       (:status filter) (notifications/only-status (:status filter)))
        pager (pager/make-pager requests :count-per-page (or (:count cursor) 50))
        current-result (cond (:previous cursor) (pager/fetch-previous pager (:previous cursor))
                             (:next cursor) (pager/fetch-next pager (:next cursor))
                             :else (pager/fetch pager))]
    {:body {:status "succeeded"
            :requests {:count (count requests)
                       :data (map api.response.notification/make-response current-result)
                       :page {:next (let [last (last current-result)]
                                      (when (and last (pager/next? pager (:id last))) (:id last)))
                              :previous (let [first (first current-result)]
                                          (when (and first (pager/previous? pager (:id first))) (:id first)))}}}}))

(defn get-request [{{:keys [request-id] :as params} :params :as req}]
  (if-let [request (notifications/request-of-app (:authenticated-app req) request-id)]
    {:body {:status "succeeded"
            :request (api.response.notification/make-response request)}}
    (api.response/error 404 :requests.not-found (format "The request %s is not found." request-id))))

(defroutes in-app-routes
  (context "/users" [] api.apps.users/dispatch)
  (POST "/all/send" [] send-notification-to-all)
  (GET "/requests" [] get-requests)
  (GET "/requests/:request-id" [] get-request))

(defn wrap-app-auth [handler]
  (fn [req]
    (if-let [app (apps/app-with-authentication (get-in req [:params :app-id]) (get-in req [:headers "x-app-secret"]))]
      (handler (assoc req :authenticated-app app))
      (api.response/error 401 :apps.authentication-failed "Authentication failed."))))

(defroutes routes
  (context "/:app-id" [] (-> in-app-routes wrap-app-auth)))

(def dispatch
  routes)
