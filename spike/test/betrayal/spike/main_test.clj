(ns betrayal.spike.main-test
  (:require [betrayal.spike.db :as db]
            [betrayal.spike.main :as main]
            [clojure.test :refer [deftest is testing]]))

(defn- with-player-stubs [f]
  (with-redefs-fn
    {#'main/ds (delay :test-datasource)
     #'db/player-in-game? (fn [datasource game-id player-id]
                            (and (= :test-datasource datasource)
                                 (= "GAME" game-id)
                                 (#{7 8} player-id)))}
    f))

(deftest resolves-local-player-overrides
  (with-player-stubs
    (fn []
      (testing "a valid loopback query parameter overrides the session"
        (is (= 8 (#'main/request-player-id
                   {:remote-addr "127.0.0.1"
                    :params {:player-id "8"}
                    :session {:player-ids {"GAME" 7}}}
                   "GAME"))))
      (testing "IPv6 loopback requests are local"
        (is (= 8 (#'main/request-player-id
                   {:remote-addr "::1"
                    :params {:player-id "8"}}
                   "GAME"))))
      (testing "unknown players fall back to the session identity"
        (is (= 7 (#'main/request-player-id
                   {:remote-addr "127.0.0.1"
                    :params {:player-id "99"}
                    :session {:player-ids {"GAME" 7}}}
                   "GAME")))))))

(deftest rejects-remote-player-overrides
  (with-player-stubs
    (fn []
      (testing "a remote query parameter cannot override the session"
        (is (= 7 (#'main/request-player-id
                   {:remote-addr "192.0.2.10"
                    :params {:player-id "8"}
                    :session {:player-ids {"GAME" 7}}}
                   "GAME"))))
      (testing "a remote query parameter cannot establish an identity"
        (is (nil? (#'main/request-player-id
                   {:remote-addr "192.0.2.10"
                    :params {:player-id "8"}}
                   "GAME")))))))
