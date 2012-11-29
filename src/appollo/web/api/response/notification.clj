(ns appollo.web.api.response.notification
  (use conceit.commons))

(defn make-response [notification-request]
  (let [content (:content notification-request)]
    (?-> {:id (:id notification-request)
          :status (:status notification-request)
          :requested-at (:requested-at notification-request)
          :type (:type notification-request)
          :content {:message (:message content)
                    :number (:number content)
                    :extend (or (:extend content) {})}}
         (= (:type notification-request) :user) (assoc :user-id (:user-id notification-request))
         (= (:type notification-request) :all) (assoc :filter (:filter notification-request))
         (:processed-at notification-request) (assoc :processed-at (:processed-at notification-request))
         (= :error (:status notification-request)) (assoc :error (:error notification-request)))))
