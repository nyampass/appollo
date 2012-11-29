(ns appollo.services.gcm
  (:use conceit.commons)
  (:require [appollo.services :as services]
            [clojure
             [string :as string]]
            [nyampass.push-notification.gcm :as gcm]))

(services/service-ns :gcm)

;; <app-settings>
;; {:api-key api-key}

(services/def-send-notification! [app-settings user notification]
  (let [{{{:keys [registration-id]} :gcm} :services} user]
    (when registration-id
      (gcm/send-notification registration-id
                             (map-map-keys #(keyword (str "data." (name-or-str %)))
                                           (merge (:extend notification)
                                                  (dissoc notification :extend)))
                             app-settings))))

(services/def-user-settings-conversion
  {:registration-id [:type :string]})
