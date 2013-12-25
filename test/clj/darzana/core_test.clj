(ns darzana.core-test
  (:require 
    [clojure.data.json :as json]
    [darzana.core :refer :all]
    [darzana.context :as context])
  (:use
    [midje.sweet]))

(facts "if-contains-test"
  (fact "if-contains"
    (let [ ctx (context/create-context {:params {:name "ABC"}})
           to-lower-case (fn [context] (update-in context [:scope :params "name"] #(.toLowerCase %1)))
           res (if-contains ctx :name (to-lower-case)) ]
      (get-in res [:scope :params]))))


