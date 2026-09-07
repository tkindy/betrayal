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
      (testing "a valid loopback query parameter identifies the player"
        (is (= 8 (#'main/debug-player-id
                   {:remote-addr "127.0.0.1"
                    :params {:player-id "8"}}
                   "GAME"))))
      (testing "IPv6 loopback requests are local"
        (is (= 8 (#'main/debug-player-id
                   {:remote-addr "::1"
                    :params {:player-id "8"}}
                   "GAME"))))
      (testing "unknown players are rejected"
        (is (nil? (#'main/debug-player-id
                   {:remote-addr "127.0.0.1"
                    :params {:player-id "99"}}
                   "GAME")))))))

(deftest rejects-remote-player-overrides
  (with-player-stubs
    (fn []
      (testing "a remote query parameter cannot establish an identity"
        (is (nil? (#'main/debug-player-id
                   {:remote-addr "192.0.2.10"
                    :params {:player-id "8"}}
                   "GAME")))))))

(deftest lists-player-specific-game-links
  (with-redefs-fn
    {#'main/ds (delay :test-datasource)
     #'db/games (fn [datasource]
                  (is (= :test-datasource datasource))
                  [{:id "GAME"
                    :name "Friday night"
                    :players [{:id 7 :name "Alex"}
                              {:id 8 :name "Blair"}]}])}
    (fn []
      (let [html (#'main/index-page)]
        (is (re-find #"/games/GAME\?player-id=7" html))
        (is (re-find #"/games/GAME\?player-id=8" html))
        (is (re-find #">Alex<" html))
        (is (re-find #">Blair<" html))))))
