(ns appollo.core
  (require [appollo.services apns c2dm gcm]
           [appollo
            [web :as web]]
           [clojure.java
            [io :as java.io]]))

(defn -main [config-file-path]
  (web/run (java.io/file config-file-path)))

;; (-main "config/development.yaml")
