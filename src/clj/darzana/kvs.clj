(ns darzana.kvs
  (:refer-clojure :exclude [get set])
  (:require [taoensso.carmine :as car]))

(def redis-connection {:pool {} :spec {:host "127.0.0.1" :port 6379}})

(defmacro with-kvs [& body]
  "Redis context wrapper"
  `(car/wcar redis-connection ~@body))

(defn get [k] (car/get k))
(defn set [k v] (car/set k v))
(defn expire [k exp] (car/expire k exp))

