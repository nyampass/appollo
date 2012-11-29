(ns appollo.utils.ring.json
  (require [appollo.utils json]
           [clojure.data
            [json :as json]]
           [ring.util.response :as response]))

(defn json-response [response & {:keys [charset] :as options}]
  (if (nil? response)
    nil
    (if (and (contains? response :status) (.startsWith (str (:status response)) "3"))
      response
      (response/content-type (if (contains? response :body)
                               (assoc response :body (json/json-str (:body response)))
                               response)
                             (str "application/json" (if charset (str "; charset=" charset) ""))))))

(defn wrap-json [handler & options]
  (fn [req]
    (apply json-response (handler req) options)))
