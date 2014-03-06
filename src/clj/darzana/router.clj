(ns darzana.router
  (:use [darzana.block])
  (:require
    [clojure.data.json :as json]
    [clojure.data.xml :as xml]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    [compojure.core :as compojure]
    [darzana.workspace :as workspace]))

(defn make-path
  ([] (.. (io/file (workspace/current-dir) "router") getPath))
  ([ws] (.. (io/file (@workspace/config :workspace) ws "router") getPath))
  ([ws router]
    (.. (io/file (@workspace/config :workspace) ws "router" (str router ".clj")) getPath)))

(def plugins (atom []))

(defn- load-blocks [blocks]
  (doseq [b blocks]
    (load (-> b (string/replace "." "/")
                (string/replace "-" "_")))
    (use (symbol b))))

(defn load-router
  ([router-file]
    (logging/debug "load router: " router-file)
    (binding [*ns* (create-ns (gensym "routing"))]
      (refer-clojure)
      (use '[darzana api]
        '[compojure.core :as compojure :only (GET POST PUT ANY defroutes)])
      (load-blocks ['darzana.block.core 'darzana.block.ab-testing])
      (load-string (str "[" (slurp router-file) "]"))))
  ([]
    (let [router-files (->> (.listFiles (io/file (make-path)))
                            (map #(.getPath %))
                            (filter #(.endsWith % ".clj")))]
      (flatten
        (for [router-file router-files]
          (load-router router-file))))))
      

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

(defn deserialize-statement [stmt]
  (seq (reduce #(apply conj %1 %2) []
       (map deserialize-block (get stmt :content)))))

(defmethod deserialize-block "marga" [block]
  (let [ props (reduce (fn [memo item] (assoc memo (get-in item [:attrs :name]) (first (get item :content)))) {}
                 (filter-children block :title))
         sexp ['defmarga (symbol (get props "method" 'GET)) (get props "path")]]
    (seq (reduce conj sexp
           (deserialize-statement (find-child block :statement))))))


(defmulti deserialize (fn [el] (get el :tag)))

(defmethod deserialize :xml [el] (deserialize (first (get el :content))))

(defmethod deserialize :block [el] (deserialize-block el))

