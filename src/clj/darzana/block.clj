(ns darzana.block)

(defmulti serialize-component (fn [s r] (first s)))
(defmethod serialize-component :default [s r] (throw (Exception. (str "Unknown component:" s))))

(defmulti deserialize-block (fn [block] (get-in block [:attrs :type])))
(defmethod deserialize-block :default [block] nil)


(defn filter-children
  ([node tag-name] (filter #(= (get % :tag) tag-name) (get node :content)))
  ([node tag-name attrs]
    (filter
      #(every? (fn [x] (= (get-in % [:attrs (first x)]) (second x))) attrs)
      (filter-children node tag-name))))

(defn find-child
  ([node tag-name] (first (filter-children node tag-name)))
  ([node tag-name attrs] (first (filter-children node tag-name attrs))))

(defn deserialize-chained-block [chained-block]
  (cond
    (coll? chained-block) (if (> (count chained-block) 1)
                            (seq (reduce conj ['->] chained-block))
                            (first chained-block))
    (nil? chained-block) (seq ['->])
    :else chained-block))

(defn deserialize-next [next]
  (first (map deserialize-block (filter-children next :block))))

(defn get-text [node]
  (let [children (get node :content)]
    (reduce str (filter string? children))))

