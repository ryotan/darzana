(ns darzana.admin.core
  (:require [compojure.core :as compojure :refer [GET]]
            [compojure.route :as route])
  (:use 
        [ring.util.response :only [resource-response]]))

(def admin-routes
  (compojure/routes
    darzana.admin.template/routes
    darzana.admin.router/routes
    darzana.admin.api/routes
    darzana.admin.git/routes
    darzana.admin.workspace/routes
    (GET "/" [] (resource-response "index.html"
                  {:root "darzana/admin/public"}))
    (route/resources "/" {:root "darzana/admin/public"} )))

