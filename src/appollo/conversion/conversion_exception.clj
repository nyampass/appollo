(ns appollo.conversion.conversion-exception
  (:gen-class :name appollo.conversion.conversion-exception
              :extends Exception
              :init init
              :constructors {[clojure.lang.IPersistentMap] [String]}
              :state error))

(defn -init [error]
  [[(str error)] error])
