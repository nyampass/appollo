(ns appollo.services.apns
  (:use conceit.commons
        [clojure.data.json :only [json-str]])
  (:require [appollo.services :as services]
            [clojure.data.codec
             [base64 :as base64]]
            [nyampass.push-notification.apns :as apns])
  (import [java.security KeyStore]
          [java.net Socket]
          [javax.net SocketFactory]
          [javax.net.ssl KeyManagerFactory SSLContext]
          [java.io FileInputStream ByteArrayOutputStream DataOutputStream]))

(services/service-ns :apns)

;; <app-settings>
;; :id - app id
;; :certification
;;     :file - p12 file
;;     :password
;; :server - "sandbox" or "production"

(def servers {:sandbox {:push {:host "gateway.sandbox.push.apple.com"
                               :port 2195}
                        :feed {:host "feedback.sandbox.push.apple.com"
                               :port 2196}}
              :production {:push {:host "gateway.push.apple.com"
                                  :port 2195}
                           :feed {:host "feedback.push.apple.com"
                                  :port 2196}}})

(def ^{:private true} socket-atoms-atom (atom {}))

(defn- key-store [{{:keys [file password]} :certification :as app-settings}]
  (doto ^KeyStore (KeyStore/getInstance "PKCS12")
    (.load ^FileInputStream (FileInputStream. ^String file) ^chars (.toCharArray ^String password))))

(defn- key-managers [{{:keys [password]} :certification :as app-settings}]
  (.getKeyManagers (doto ^KeyManagerFactory (KeyManagerFactory/getInstance "SunX509")
                     (.init ^KeyStore (key-store app-settings) ^chars (.toCharArray ^String password)))))

(defn- connect [app-settings]
  (let [server (get-in servers [(keyword (:server app-settings)) :push])]
    (.createSocket ^SocketFactory (.getSocketFactory (doto ^SSLContext (SSLContext/getInstance "TLS" "SunJSSE")
                                                           (.init (key-managers app-settings) nil nil)))
                   ^String (:host server)
                   (:port server))))

(defn- connected-socket [app-settings & {:keys [force]}]
  (let [socket-atom (or (get @socket-atoms-atom (:id app-settings)) (atom nil))
        socket ^Socket @socket-atom]
    (if (and (not force) socket (.isConnected socket))
      socket
      (let [socket (connect app-settings)]
        (reset! socket-atom socket)
        (swap! socket-atoms-atom assoc (:id app-settings) socket-atom)
        socket))))

(defmacro ^{:private true} with-socket [[socket app-settings & options] & body]
  `(let [~socket (connected-socket ~app-settings ~@options)]
     (when (and ~socket (.isConnected ~socket))
       (locking ~socket
         ~@body))))

(defn- payload-from-notification [notification]
  (merge (:extend notification)
         {:aps {:alert (:message notification)
                :badge (:number notification)
                :sound :default}}))
         

(def ^{:private true} expiry (* 60 60 24))

(services/def-send-notification! [app-settings user notification]
  (let [{{{:keys [device-token]} :apns} :services} user]
    (when device-token
      (letfn [(send-to-socket [socket]
                (apns/send-notification socket
                                        :extended
                                        :device-token (base64/decode (.getBytes ^String device-token))
                                        :payload (payload-from-notification notification)
                                        :identifier (.getBytes ^String (apply str (concat (:id user) (repeat 4 \space))))
                                        :expiry expiry))]
        (with-socket [socket app-settings]
          (try (send-to-socket socket)
               (catch Exception _
                 (with-socket [socket app-settings :force true]
                   (send-to-socket socket)))))))))

(def ^{:private true} device-token-bytes 32)

(services/def-user-settings-conversion
  {:device-token [:type :string
                  :validate [#(let [decoded (base64/decode (.getBytes ^String %))]
                                (and (= (count decoded) device-token-bytes)
                                     (= % (String. (base64/encode decoded))))) :error :invalid-format]]})
