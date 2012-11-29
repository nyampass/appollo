(ns appollo.cursor
  (use conceit.commons)
  (require [mongoika :as mongo]))

(defn as-cursor [cursor-type query]
  (assoc-meta (mongo/query query) ::cursor-type cursor-type))

(defn cursor-type [cursor]
  (-> cursor meta ::cursor-type))

(defmulti only-next (fn [key cursor] (cursor-type cursor)))

(defmulti only-previous (fn [key cursor] (cursor-type cursor)))
