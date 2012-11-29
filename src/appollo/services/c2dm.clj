(ns appollo.services.c2dm
  (use conceit.commons)
  (:require [appollo.services :as services]
            [clojure
             [string :as string]]
            [nyampass.push-notification.c2dm :as c2dm]))

(services/service-ns :c2dm)

;; <app-settings>
;; :id - app id
;; :authorization
;;    :email
;;    :password
;;    :source

(def ^{:private true} auth-tokens-atom (atom {}))

(defn- get-auth-token [{{:keys [email password source]} :authorization :as app-settings}]
  (c2dm/auth-token email password source))

(defn- auth-token [app-settings]
  (let [app-id (:id app-settings)]
    (or (get @auth-tokens-atom app-id)
        (let [auth-token (get-auth-token app-settings)]
          (swap! auth-tokens-atom assoc app-id auth-token)
          auth-token))))

(services/def-send-notification! [app-settings user notification]
  (when-not (get-in user [:services :gcm :registration-id]) ; don't send with C2DM if the user is registered with GCM
    (let [{{{:keys [registration-id]} :c2dm} :services} user]
      (when registration-id
        (let [data (merge (:extend notification) (dissoc notification :extend))]
          (c2dm/send-notification registration-id data (auth-token app-settings)))))))

(services/def-user-settings-conversion
  {:registration-id [:type :string]})
