(ns betrayal.spike.test-runner
  (:require [betrayal.spike.board-test]
            [clojure.test :as test]))

(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests 'betrayal.spike.board-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
