(ns appollo.web.routes
  (use [compojure.core :only [defroutes context]])
  (require [appollo.web
            [api :as web.api]]
           [mongoika :as mongo]
           [compojure.core :as compojure]
           [compojure.route :as route]
           compojure.handler
           ring.middleware.reload)
  (import [java.util Date]))

(defroutes routes
  (context "/api" [] web.api/dispatch)
  (route/not-found "Not Found"))

(defn- wrap-mongo [handler {:keys [db] :as config}]
  (let [mongo (mongo/mongo (:instances db) (:options db))]
    (fn [req]
      (mongo/with-db-binding (mongo/database mongo (:name db))
        (mongo/with-request (mongo/bound-db)
          (when (and (:user db) (:password db))
            (mongo/authenticate (:user db) (:password db)))
          (handler req))))))

(defn- wrap-date [handler]
  (fn [req]
    (handler (assoc req :requested-at (Date.)))))

(defn- wrap-log [handler]
  (fn [req]
    (println (format "[%s] %s %s %s"
                     (str (Date.))
                     (-> req :request-method name .toUpperCase)
                     (:uri req)
                     (:params req)))
    (handler req)))

(defn- wrap [routes config]
  (-> routes
      (wrap-mongo config)
      wrap-log
      wrap-date
      compojure.handler/site
      ((if (-> config :development :wrap-reload)
         #(ring.middleware.reload/wrap-reload % '(appollo.web.routes))
         identity))))

(defn make-dispatcher [config]
  (compojure/routes (route/files "/")
                    (wrap routes config)))
