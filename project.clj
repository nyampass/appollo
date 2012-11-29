(defproject appollo "0.6.1"
  :description "Push notification server"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [compojure "1.0.1"]
                 [clj-yaml "0.3.1"]
                 [org.clojure/data.json "0.1.3"]
                 [org.clojure/data.codec "0.1.0"]
                 [conceit/conceit-commons "1.0.21"]
                 [mongoika "0.6.9"]
                 [push-notification "1.1.2"]]
  :dev-dependencies [[lein-ring "0.4.6"]
                     [swank-clojure "1.4.0"]]
  :main appollo.core)
