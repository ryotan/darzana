(ns darzana.context
  (:require
    [clojure.tools.logging :as log]))

(def application-scope (ref {}))

(def scope-priorities
  [ :error :page :params :session :application])

(defn keyword-to-str [v]
  (cond
    (keyword? v) (name v)
    (map? v)     (if (empty? v)
                   v
                   (apply assoc {} (interleave (map name (keys v)) (keyword-to-str (vals v))))) 
    (coll?  v)   (map keyword-to-str v)
    :else v))

(defn create-context [request]
  { :scope { :application (keyword-to-str @application-scope)
             :session     (get request :session {})
             :params      (keyword-to-str (get request :params {}))
             :page        {}
             :error       {}
             :cookies     (get request :cookies {}) }
    :session-add-keys    {}
    :session-delete-keys []
    :request request})

(defn merge-scope [context]
  (apply merge (vals (context :scope))))

(defn- find-in-scopes-inner [context key]
  (cond
    (string? key) key
    (number? key) (str key)
    :else
    (let [keys (if (coll? key)
                 (map name key)
                 (name key))]
      (first
        (filter #(not (nil? %))
          (for [scope-name scope-priorities]
            (get-in (context :scope) (flatten [scope-name keys]))))))))

(defn find-in-scopes
  ([context key]
    (find-in-scopes-inner context key))
  ([context key not-found]
    (let [ value (find-in-scopes-inner context key) ]
      (if (nil? value) not-found value))))

