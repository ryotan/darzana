(ns darzana.core
  (:use
    [compojure.core :as compojure :only (GET)]
    [darzana.router :only (load-router)])
  (:require
    [clojure.java.io :as io]
    [darzana.context :as context]
    [darzana.workspace :as workspace]
    [darzana.admin router template api git workspace]))

(def router (atom nil))

(defmacro defblock [block-name & body]
  "Define a block component for cljs."
  `(aset js/Blockly.Language ~(name block-name) ~@body))

(defn set-application-scope [scope]
  (reset! context/application-scope scope))

(defn find-api [apis name]
  (first
    (filter #(= name (get % :name)) apis)))

(defn load-routes []
  (reset! router (load-router))
  (compojure/routes
    (GET "/router/reload" [] (do (load-routes) "reloaded."))
    (fn [request] (some #(% request) @router))))

(swap! workspace/config update-in [:hook :change] conj
  load-routes)
