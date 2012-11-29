(ns appollo.web
  (require [appollo
            [config :as config]]
           [appollo.web
            [routes :as web.routes]]
           [ring.adapter.jetty :as jetty])
  (import [java.util TimeZone]))

(defn run [config-file]
  (let [config (config/load-config config-file)
        port (or (-> config :http :port) 80)]
    (do (TimeZone/setDefault (TimeZone/getTimeZone "GMT"))
        (jetty/run-jetty (web.routes/make-dispatcher config)
                         {:join false
                          :port port}))))
