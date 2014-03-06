(ns darzana.context-test
  (:require
    [darzana.context :refer :all])
  (:use
    [midje.sweet]
    [darzana.core :only [set-application-scope]]))

(facts "context"
  (fact "convert keyword to string."
    (keyword-to-str {:a 1, :b 2}) => {"a" 1, "b" 2})

  (fact "convert keyword to string."
    (keyword-to-str {:a 1, :b {:b1 "2" :b2 "3"}}) => {"a" 1, "b" {"b1" "2", "b2" "3"}})

  (fact "convert keyword to string."
    (keyword-to-str {}) => {})

  (fact "convert nil to nil"
    (keyword-to-str nil) => nil)
  
  (fact "convert keywords in vector."
    (keyword-to-str [:a :b :c "d" :e]) => ["a" "b" "c" "d" "e"])

  (fact "convert keywords in sequence."
    (keyword-to-str '(:a :b :c "d" :e)) => '("a" "b" "c" "d" "e"))
  
  (fact "merge scope normally."
    (set-application-scope {})
    (let [ctx (assoc-in (create-context {:session {} :params {}}) [:scope :params "a"] 1)]
      (merge-scope ctx) => {"a" 1}))

  (fact "Merge scope."
    (set-application-scope {})
    (let [ctx (assoc-in (create-context {:session {:a 3} :params {}}) [:scope :params "a"] 1)]
      (merge-scope ctx) => {"a" 1}))

  (fact "Merge scope."
    (set-application-scope {})
    (let [ctx (reduce #(apply assoc-in %1 %2) (create-context {:session {:a 2} :params {}})
                [[[:scope :session "a"] 1 ] [[:scope :params "a"] 8]])]
      (merge-scope ctx) => {"a" 8})))
