(ns appollo.utils.jsons
  (require [clojure.data.json :as json])
  (import java.text.SimpleDateFormat
          java.io.PrintWriter))

(def date-format (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss Z"))

(extend-protocol json/Write-JSON
  java.util.Date
  (write-json [x ^PrintWriter out escape-unicode?]
    (json/write-json (.format date-format x) out escape-unicode?))
  Object
  (write-json [x ^PrintWriter out escape-unicode?]
    (json/write-json (str x) out escape-unicode?)))
