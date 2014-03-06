(ns darzana.block.core
  (:use [darzana.block]
        [darzana.template :only (handlebars)])
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as logging]
            [darzana.api :as api]
            [darzana.context :as context]))

(defn- call-api-internal [context apis]
  (for [result (doall (map #(api/execute-api context %) apis))]
    (let [api (result :api)]
      (if (result :from-cache) 
        (do
          (logging/debug "API response(from cache)" (result :response))
          {:page {(name (api :name)) (result :response)} }) ;; From Cache
        (let [ response (api/parse-response @(result :response))
               cache-key (str (api :name) "-" (get-in @(result :response) [:opts :url]))]
          (logging/debug "API response" @(result :response))
          (if ((api :success?) @(result :response))
            (do
              (api/cache-response response cache-key api)
              {:page {(name (api :name)) response}})
            { :error
              {(name (api :name))
                { "status"   (-> result :response deref :status)
                  "message" response}}}))))))

(defn call-api [context api]
  (let [ apis (if (map? api) [api] api)
         api-responses (call-api-internal context apis)]
    (assoc context :scope
      (reduce #(merge-with merge %1 %2) (context :scope) api-responses))))

(defmacro if-success [context success error]
  `(if (empty? (~context :error))
     (-> ~context ~success)
     (-> ~context ~error)))

(defmacro if-contains
  ([context key contains]
    `(if-contains ~context ~key ~contains do))
  ([context key contains not-contains]
    `(if (context/find-in-scopes ~context ~key)
       (-> ~context ~contains)
       (-> ~context ~not-contains))))

(defn store-session [context & session-keys]
  (apply merge-with merge context
    (map (fn [_] {:session-add-keys
                   (if (vector? _)
                     (apply hash-map (reverse _))
                     (hash-map _ _))})
      session-keys)))

(defn- save-session [response context]
  (let [ session (->
                   (get-in context [:scope :session])
                   (#(reduce dissoc % (context :session-remove-keys)))
                   (#(reduce
                       (fn [m k] (apply assoc m k)) %
                       (for [[session-key context-keys] (context :session-add-keys)]
                         [(name session-key) (context/find-in-scopes context context-keys)]))))]
    (if (empty? session)
      response
      (assoc response :session session))))

(defn- save-cookies [response context]
  (assoc response :cookies
    (get-in context [:scope :cookies])))

(defn render [ctx template]
  (-> (ring.util.response/response (.apply (.compile @handlebars template) (context/merge-scope ctx)))
    (ring.util.response/content-type (context/find-in-scopes ctx :content-type "text/html"))
    (ring.util.response/charset (context/find-in-scopes ctx :charset "UTF-8"))
    (save-session ctx)
    (save-cookies ctx)))

(defn redirect
  ([context url]
    (redirect context url nil))
  ([context url options]
    (-> (ring.util.response/redirect
          (if options
            (api/build-url context
              { :url url
                :method :get
                :query-keys (options :query-keys)})
            url))
      (save-session context)
      (save-cookies context))))

(defmacro defmarga [method url & exprs ]
  `(~method ~url {:as request#}
     (-> (context/create-context request#) ~@exprs)))


;;; Serialize

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


(defmethod deserialize-block "api_list" [block]
  (apply conj [] (map #(deserialize-block (first (get % :content))) (filter-children block :value))))

(defmethod deserialize-block "api" [block]
  (symbol (get-text (find-child block :title))))

(defn deserialize-api-value [value]
  (deserialize-block (find-child value :block)))

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
