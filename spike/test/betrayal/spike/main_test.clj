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

(deftest disables-player-overrides-in-production
  (with-redefs-fn
    {#'main/production? (constantly true)}
    (fn []
      (is (nil? (#'main/debug-player-id
                 {:remote-addr "127.0.0.1"
                  :params {:player-id "8"}}
                 "GAME"))))))

(deftest resolves-signed-session-identity
  (with-player-stubs
    (fn []
      (testing "a valid player stored in the session identifies the client"
        (is (= 7 (#'main/request-player-id
                   {:remote-addr "192.0.2.10"
                    :session {:player-ids {"GAME" 7}}}
                   "GAME"))))
      (testing "a remote query parameter cannot replace the session identity"
        (is (= 7 (#'main/request-player-id
                   {:remote-addr "192.0.2.10"
                    :params {:player-id "8"}
                    :session {:player-ids {"GAME" 7}}}
                   "GAME"))))
      (testing "the local development override takes precedence"
        (is (= 8 (#'main/request-player-id
                   {:remote-addr "127.0.0.1"
                    :params {:player-id "8"}
                    :session {:player-ids {"GAME" 7}}}
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
      (let [html (#'main/index-page {:remote-addr "127.0.0.1"})]
        (is (re-find #"/games/GAME\?player-id=7" html))
        (is (re-find #"/games/GAME\?player-id=8" html))
        (is (re-find #">Alex<" html))
        (is (re-find #">Blair<" html))))))

(deftest remote-game-list-establishes-a-session
  (with-redefs-fn
    {#'main/ds (delay :test-datasource)
     #'db/games (fn [_]
                  [{:id "GAME"
                    :name "Friday night"
                    :players [{:id 7 :name "Alex"}]}])}
    (fn []
      (let [html (#'main/index-page {:remote-addr "192.0.2.10"})]
        (is (re-find #"method=\"post\"" html))
        (is (re-find #"action=\"/games/GAME/player\"" html))
        (is (re-find #"name=\"player-id\"[^>]+value=\"7\"" html))
        (is (not (re-find #"\?player-id=" html)))))))

(deftest selecting-a-player-stores-the-game-binding
  (with-player-stubs
    (fn []
      (let [response (#'main/select-player
                      {:params {:player-id "8"}
                       :session {:unrelated "value"}}
                      "GAME")]
        (is (= 303 (:status response)))
        (is (= "/games/GAME" (get-in response [:headers "Location"])))
        (is (= "value" (get-in response [:session :unrelated])))
        (is (= 8 (get-in response [:session :player-ids "GAME"])))))))

(deftest websocket-commands-receive-exact-acknowledgements
  (let [channel ::channel
        sent (atom [])
        clients (atom {channel {:game-id "GAME" :player-id 7}})]
    (with-redefs-fn
      {#'main/clients clients
       #'main/run-action! (fn [game-id player-id action params]
                            (is (= ["GAME" 7 "roll" "command-123"]
                                   [game-id player-id action (:id params)]))
                            #{:dice})
       #'main/broadcast-state! (fn [game-id regions]
                                (is (= ["GAME" #{:dice}]
                                       [game-id regions])))
       #'main/send-message! (fn [actual-channel message]
                              (swap! sent conj [actual-channel message]))}
      (fn []
        (#'main/receive-command!
         channel
         "{\"id\":\"command-123\",\"command\":\"action\",\"action\":\"roll\"}")
        (is (= [[channel {:type "ack" :id "command-123"}]]
               @sent))))))

(deftest websocket-errors-retain-the-command-id
  (let [channel ::channel
        errors (atom [])
        clients (atom {channel {:game-id "GAME" :player-id 7}})]
    (with-redefs-fn
      {#'main/clients clients
       #'main/run-action! (fn [& _]
                            (throw (ex-info "No dice for you" {})))
       #'main/send-state! (fn [& args] (swap! errors conj args))}
      (fn []
        (#'main/receive-command!
         channel
         "{\"id\":\"command-456\",\"command\":\"action\",\"action\":\"roll\"}")
        (is (= [[channel "error" "No dice for you" #{:all} "command-456"]]
               @errors))))))

(deftest trait-actions-only-invalidate-that-players-traits
  (with-redefs-fn
    {#'main/ds (delay :test-datasource)
     #'db/set-trait! (fn [datasource game-id player-id trait index]
                       (is (= [:test-datasource "GAME" 7 "speed" 3]
                              [datasource game-id player-id trait index])))}
    (fn []
      (is (= #{[:traits 7]}
             (#'main/run-action!
              "GAME" 8 "set-trait"
              {:player-id "7" :trait "speed" :index "3"}))))))

(deftest room-menu-actions-update-the-board
  (with-redefs-fn
    {#'main/ds (delay :test-datasource)
     #'db/rotate-room! (fn [datasource game-id room-id]
                         (is (= [:test-datasource "GAME" 12]
                                [datasource game-id room-id])))
     #'db/return-room! (fn [datasource game-id room-id]
                         (is (= [:test-datasource "GAME" 12]
                                [datasource game-id room-id])))}
    (fn []
      (is (= #{:board}
             (#'main/run-action!
              "GAME" 7 "rotate-room" {:room-id "12"})))
      (is (= #{:board :room-stack}
             (#'main/run-action!
              "GAME" 7 "return-room" {:room-id "12"}))))))

(deftest health-check-does-not-require-the-database
  (let [response (main/app {:request-method :get
                            :uri "/up"
                            :headers {}})]
    (is (= 200 (:status response)))
    (is (= "OK" (:body response)))))
