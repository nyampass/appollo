(ns appollo.config
  (require [clj-yaml.core :as yaml]))

(defn load-config [file]
  (yaml/parse-string (slurp file)))
