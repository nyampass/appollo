(ns appollo.web.api.response.user
  (use conceit.commons))

(defn make-response [user]
  {:id (:id-in-app user)
   :test (boolean (:test? user))
   :excluded (boolean (:excluded? user))
   :registered-at (:registered-at user)
   :updated-at (:updated-at user)})
