(ns appollo.web.api
  (use conceit.commons
       [compojure.core :only [defroutes context]])
  (require appollo.conversion.conversion-exception
           [appollo.web.api
            [response :as api.response]
            [apps :as api.apps]]
           [appollo.utils.ring
            [json :as ring.json]])
  (import appollo.conversion.conversion-exception))

(defn not-found [req]
  (api.response/not-found :api.not-found (format "The requested uri \"%s\" doesn't exist." (:path-info req))))

(defroutes routes
  (context "/apps" [] api.apps/dispatch)
  #(not-found %))

(defn wrap-api-error [handler]
  (fn [req]
    (try (handler req)
         (catch conversion-exception e
           (api.response/invalid-parameters (.error e)))
         (catch Exception e
           (.printStackTrace e)
           (api.response/internal-error e)))))

(def dispatch
  (-> routes
      wrap-api-error
      (ring.json/wrap-json :charset "UTF-8")))
