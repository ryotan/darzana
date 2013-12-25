(ns darzana.ab-testing-test
  (:require
    [darzana.ab-testing :refer :all])
  (:use
    [darzana.context :only (create-context)]
    [midje.sweet]))

(facts "participate"
  (fact "participate macro."
    (let [ctx (assoc-in (create-context {:session {} :params {}}) [:scope :session :a] 1)]
      (-> ctx (ab-testing-participate "new test"
                (ab-testing-alternative "Blue" (assoc-in [:scope :params "color"] "BLUE")))))))

(ns darzana.ab-testing)
(defn participate-sixpack [experiment client-id alternatives]
  (rand-nth alternatives))

