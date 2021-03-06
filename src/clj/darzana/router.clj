(ns darzana.router
  (:require
    [clojure.data.json :as json]
    [clojure.data.xml :as xml]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [me.raynes.fs :as fs]
    [darzana.workspace :as workspace]))

(defn make-path
  ([] (.. (io/file (workspace/current-dir) "router") getPath))
  ([ws] (.. (io/file (@workspace/config :workspace) ws "router") getPath))
  ([ws router]
    (.. (io/file (@workspace/config :workspace) ws "router" (str router ".clj")) getPath)))

(def route-namespace (atom nil))
(def plugins (atom []))

(defn load-app-routes []
  (if (nil? @route-namespace) (reset! route-namespace *ns*))
  (binding [*ns* @route-namespace]
    (let [ code-routing (string/join " "
        (flatten 
          [ "(use ['darzana.core] ['compojure.core :as 'compojure :only '(GET POST PUT ANY defroutes)]"
            (map (fn [_] [" ['" _ "]"]) @plugins) ")"
            "(defroutes app-routes"
            (map #(slurp %)
              (fs/glob (io/file (make-path)) "*.clj"))
            ")"]))]
      (load-string code-routing) 
      )))

(defmulti serialize-api (fn [x] (coll? x)))

(defmethod serialize-api true [apis]
  [:block {:type "api_list" :inline false}
    [:mutation {:items (count apis)}]
    (map-indexed (fn [idx api]
                   [:value {:name (str "API" idx)}
                     (serialize-api api)]) apis)])

(defmethod serialize-api false [api]
  [:block {:type "api"}
    [:title {:name "api"} api]])

(defmulti serialize-keys (fn [x] (type x)))

(defmethod serialize-keys clojure.lang.PersistentVector [ks]
  [:block {:type "key_composite" :inline true}
    (map-indexed (fn [i _] [:value {:name (str "KEY" i)} (serialize-keys _)]) ks)])

(defmethod serialize-keys clojure.lang.Keyword [ks]
  [:block {:type "key_keyword"}
    [:title {:name "KEYWORD"} (name ks)]])

(defmethod serialize-keys java.lang.String [ks]
  [:block {:type "key_literal"}
    [:title {:name "STRING"} ks]])

(defmethod serialize-keys :default [ks] (throw (Exception. (str "Parse error in keys:" ks))))

(defmulti serialize-component (fn [s r] (first s)))

(defmethod serialize-component 'assign [s r]
  [:block {:type "key_assign" :inline true}
    [:value {:name "FROM"} (serialize-keys (nth s 1))]
    [:value {:name "TO"}   (serialize-keys (nth s 3))]])

(defmethod serialize-component 'call-api [s r]
  (let [elm [:block {:type "call_api" :inline true}
              [:value {:name "API"}
                (serialize-api (second s))]]]
    (if (empty? r) elm
      (conj elm [:next (serialize-component (first r) (rest r))]))))

(defmethod serialize-component 'render [s r]
  [:block {:type "render"}
    [:title {:name "template"} (second s)]])

(defmethod serialize-component 'redirect [s r]
  [:block {:type "redirect"}
    [:title {:name "url"} (second s)]])

(defmethod serialize-component 'if-success [s r]
  (let [elm [:block {:type "if_success"}
                [:statement {:name "success"} (serialize-component (nth s 1) nil)]
                [:statement {:name "error"}   (serialize-component (nth s 2) nil)]]]
    (if (empty? r) elm
      (conj elm [:next (serialize-component (first r) (rest r))]))))

(defmethod serialize-component 'if-contains [s r]
  (let [elm [:block {:type "if_contains"}
              [:title {:name "key"} (name (nth s 1))]
              [:statement {:name "contains"} (serialize-component (nth s 2) nil)]
              [:statement {:name "not-contains"} (serialize-component (nth s 3) nil)]]]
    (if (empty? r) elm
      (conj elm [:next (serialize-component (first r) (rest r))]))))

(defmethod serialize-component 'store-session [s r]
  (let [elm [:block {:type "store_session"}
              (map-indexed
                (fn [i _]
                  [:value {:name (str "KEY" i)}
                    (if (seq? _)
                      (serialize-component _ nil)
                      [:block {:type "key_keyword"} [:title {:name "KEYWORD"} (name _)]])])
                (drop 1 s)) ]]
    (if (empty? r) elm
      (conj elm [:next (serialize-component (first r) (rest r))]))))

(defmethod serialize-component '-> [s r]
  (let [chain-block (rest s)]
    (when (not-empty chain-block)
      (serialize-component (first chain-block) (rest chain-block)))))

(defmethod serialize-component :default [s r] (throw (Exception. (str "Unknown component:" s))))

(defn serialize-statement [sexp]
  (if (not-empty sexp)
  [:statement {:name "component"} 
    (serialize-component (first sexp) (rest sexp))]))

(defn serialize [sexp]
  (xml/emit-str
    (xml/sexp-as-element 
      [:xml {}
        [:block {:type "marga" :x 180 :y 20}
          [:title {:name "method"} (nth sexp 1)]
          [:title {:name "path"} (nth sexp 2)]
          (serialize-statement (drop 3 sexp))]])))

(defn filter-children
  ([node tag-name] (filter #(= (get % :tag) tag-name) (get node :content)))
  ([node tag-name attrs]
    (filter
      #(every? (fn [x] (= (get-in % [:attrs (first x)]) (second x))) attrs)
      (filter-children node tag-name))))

(defn find-child
  ([node tag-name] (first (filter-children node tag-name)))
  ([node tag-name attrs] (first (filter-children node tag-name attrs))))

(def deserialize-block)

(defn deserialize-statement [stmt]
  (seq (reduce #(apply conj %1 %2) []
       (map deserialize-block (get stmt :content)))))

(defn deserialize-next [next]
  (first (map deserialize-block (filter-children next :block))))

(defn get-text [node]
  (let [children (get node :content)]
    (reduce str (filter string? children))))

(defn deserialize-api-value [value]
  (deserialize-block (find-child value :block)))

(defn deserialize-chained-block [chained-block]
  (cond
    (coll? chained-block) (if (> (count chained-block) 1)
                            (seq (reduce conj ['->] chained-block))
                            (first chained-block))
    (nil? chained-block) (seq ['->])
    :else chained-block))

(defmulti deserialize-block (fn [block] (get-in block [:attrs :type])))

(defmethod deserialize-block "marga" [block]
  (let [ props (reduce (fn [memo item] (assoc memo (get-in item [:attrs :name]) (first (get item :content)))) {}
                 (filter-children block :title))
         sexp ['defmarga (symbol (get props "method" 'GET)) (get props "path")]]
    (seq (reduce conj sexp
           (deserialize-statement (find-child block :statement))))))

(defmethod deserialize-block "api_list" [block]
  (apply conj [] (map #(deserialize-block (first (get % :content))) (filter-children block :value))))

(defmethod deserialize-block "api" [block]
  (symbol (get-text (find-child block :title))))

(defmethod deserialize-block "call_api" [block]
  (let [sexp (seq ['call-api (deserialize-api-value (first (filter-children block :value)))])]
    (reduce #(apply conj %1 %2) [sexp]
      (map deserialize-next (filter-children block :next)))))

(defmethod deserialize-block "render" [block]
  [(seq ['render (get-text (find-child block :title))])])

(defmethod deserialize-block "redirect" [block]
  [(seq ['redirect (get-text (find-child block :title))])])

(defmethod deserialize-block "if_success" [block]
  (let [sexp (seq ['if-success
                    (deserialize-block (find-child (find-child block :statement {:name "success"}) :block))
                    (deserialize-block (find-child (find-child block :statement {:name "error"}) :block))])]
    (reduce #(apply conj %1 %2) [sexp]
      (map deserialize-next (filter-children block :next)))))

(defmethod deserialize-block "if_contains" [block]
  (let [sexp (seq ['if-contains
                    (-> block (find-child :title {:name "key"}) (get-text) (keyword))
                    (-> block (find-child :statement {:name "contains"})
                      (find-child :block) (deserialize-block)
                      (deserialize-chained-block))
                    (-> block (find-child :statement {:name "not-contains"})
                      (find-child :block) (deserialize-block)
                      (deserialize-chained-block))])]
    (reduce #(apply conj %1 %2) [sexp]
      (map deserialize-next (filter-children block :next)))))

(defmethod deserialize-block "store_session" [block]
  (let [sexp (seq
               (apply conj ['store-session]
                 (map #(deserialize-block (find-child % :block))  (filter-children block :value))))]
    (reduce #(apply conj %1 %2) [sexp]
      (map deserialize-next (filter-children block :next)))))

(defmethod deserialize-block "key_assign" [block]
  (seq [ 'assign
         (deserialize-block (find-child (find-child block :value { :name "FROM" }) :block))
         '=>
         (deserialize-block (find-child (find-child block :value { :name "TO"   }) :block))]))

(defmethod deserialize-block "key_keyword" [block]
  (-> block (find-child :title {:name "KEYWORD"}) (get-text) (keyword)))

(defmethod deserialize-block "key_literal" [block]
  (-> block (find-child :title {:name "STRING"}) (get-text)))

(defmethod deserialize-block "key_composite" [block]
  (vec (map #(deserialize-block (find-child % :block)) (filter-children block :value))))

(defmethod deserialize-block :default [block] nil)

(defmulti deserialize (fn [el] (get el :tag)))

(defmethod deserialize :xml [el] (deserialize (first (get el :content))))

(defmethod deserialize :block [el] (deserialize-block el))

(dosync (alter workspace/config update-in [:hook :change] conj
          load-app-routes))

