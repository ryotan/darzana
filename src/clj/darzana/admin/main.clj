(ns darzana.admin.main
  (:require [compojure.core :as compojure :refer [GET]]
            [compojure.handler :as handler]
            [clojure.java.io :as io]
            [taoensso.carmine.ring :refer [carmine-store]]
            [darzana.core :refer [load-routes]]
            [darzana.workspace :as workspace]
            [darzana.kvs :as kvs]
            [darzana.admin.core :as admin]))

(defn create-admin-app []
  (let [app-scope (io/file "dev-resources/app_scope.clj")]
    (when (.exists app-scope)
      (-> app-scope (.getPath) load-file))
    (load-file "dev-resources/api.clj")
    (workspace/change-workspace "master")
    (handler/site
      (compojure/routes (load-routes)
        (compojure/context "/admin" [] admin/admin-routes))
      {:session { :store (carmine-store kvs/redis-connection)}})))

(def admin-app (create-admin-app))

