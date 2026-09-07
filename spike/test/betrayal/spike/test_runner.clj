(ns betrayal.spike.test-runner
  (:require [betrayal.spike.board-test]
            [betrayal.spike.main-test]
            [betrayal.spike.ui-test]
            [clojure.test :as test]))

(defn -main [& _]
  (let [{:keys [fail error]}
        (test/run-tests 'betrayal.spike.board-test
                        'betrayal.spike.main-test
                        'betrayal.spike.ui-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
