(ns darzana.api-test
  (:require 
    [clojure.data.json :as json]
    [darzana.core :refer :all]
    [darzana.context :as context])
  (:use
    [midje.sweet]
    [darzana.api :only [build-url build-request build-request-body build-request-headers]]))

(facts "api tests"
  (fact "Replace placeholders in url to param values."
    (let [ ctx (context/create-context {:session {} :params {:id "1"}})
           api {:url "http://example.com/api/:id"}]
      (build-url ctx api) => "http://example.com/api/1"))
  
  (fact ""
    (let [ ctx (context/create-context {:session {} :params {:id "1" :name "name"}})
           api {:url "http://example.com/api/:id"}]
      (build-url ctx api) => "http://example.com/api/1"))

  (fact ""
    (let [ ctx (context/create-context {:session {} :params {:id "1" :name "name"}})
           api {:url "http://example.com/api/:id", :method :get, :query-keys [[:id :id] [:name :name]]}]
      (build-url ctx api) => "http://example.com/api/1?id=1&name=name"))

  (fact "Build request body."
    (let [ ctx (context/create-context {:session {} :params {:id "1" :name "name"}})
           api {:url "http://example.com/api/", :method :post}]
      (build-request-body ctx api) => ""))

  (fact "Build request body."
    (let [ ctx (context/create-context {:session {} :params {:id "1" :name "name"}})
           api {:url "http://example.com/api/", :method :post, :query-keys [[:id :id] [:name :name]]}]
      (build-request-body ctx api) => "id=1&name=name"))

  (fact "Build request body."
    (let [ ctx (context/create-context {:session {} :params {:id "1" :name "name"}})
           api { :url "http://example.com/api/"
                 :method :post
                 :query-keys [[:id :id] [:name :name]],
                 :content-type "application/json"}]
      (build-request-body ctx api) => (json/write-str {:id "1" :name "name"})))

  (fact "Build oauth token header."
    (let [ ctx (context/create-context {:session {:access_token "hoge"}})
           api {:url "http://example.com/api/", :oauth-token :access_token}]
      (get (build-request-headers ctx api) "Authorization") => "Bearer hoge"))

  (fact "Build no oauth token header."
    (let [ ctx (context/create-context {:session {}})
           api {:url "http://example.com/api/", :oauth-token :access_token}]
      (get (build-request-headers ctx api) "Autorization") => nil))

  (fact "When method is post, content-type is x-www-form-urlencoded"
    (let [ ctx (context/create-context {:session {}})
           api {:url "http://example.com/api/", :method :post}]
      (get (build-request-headers ctx api) "Content-Type") => "application/x-www-form-urlencoded"))

  (fact "When method is get, there is no Content-Type."
    (let [ ctx (context/create-context {:session {}})
             api {:url "http://example.com/api/"}]
        (get (build-request-headers ctx api) "Content-Type") => nil))

  (fact "Build request basic auth"
    (set-application-scope {:consumer-key "consumer" :consumer-secret "secret"})
    (let [ ctx (context/create-context {:session {}})
           api {:url "http://example.com/api/" :basic-auth [:consumer-key :consumer-secret]}
           request (build-request {} ctx api)]
      (request :basic-auth) => ["consumer" "secret"])))



