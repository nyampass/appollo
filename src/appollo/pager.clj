(ns appollo.pager
  (use conceit.commons)
  (require [appollo
            [cursor :as cursor]]
           [mongoika :as mongo]))

(defn make-pager [cursor & {:keys [count-per-page] :as options}]
  (assoc options :cursor cursor))

(defn fetch [pager]
  (mongo/limit (:count-per-page pager) (:cursor pager)))

(defn fetch-next [pager key]
  (mongo/limit (:count-per-page pager) (cursor/only-next key (:cursor pager))))

(defn fetch-previous [pager key]
  (reverse (mongo/limit (:count-per-page pager) (mongo/reverse-order (cursor/only-previous key (:cursor pager))))))

(defn next? [pager key]
  (boolean (mongo/fetch-one (mongo/project :_id (cursor/only-next key (:cursor pager))))))

(defn previous? [pager key]
  (boolean (mongo/fetch-one (mongo/project :_id (cursor/only-previous key (:cursor pager))))))
