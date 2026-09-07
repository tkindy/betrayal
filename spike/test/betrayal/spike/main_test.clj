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
        (is (re-find #"/assets/vendor/htmx-4\.0\.0/htmx\.min\.js" html))
        (is (re-find #"/assets/vendor/htmx-4\.0\.0/hx-sse\.min\.js" html))
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

(deftest creates-a-lobby-and-binds-the-host-session
  (let [lobbies (atom {})]
    (with-redefs-fn
      {#'main/lobbies lobbies
       #'main/next-lobby-id (constantly "ABCDEF")}
      (fn []
        (let [response (#'main/create-lobby
                        {:params {:player-name "Alex"}
                         :session {}})
              token (get-in response [:session :lobby-ids "ABCDEF"])]
          (is (= 303 (:status response)))
          (is (= "/lobbies/ABCDEF" (get-in response [:headers "Location"])))
          (is (string? token))
          (is (= {:id "ABCDEF"
                  :game-name "Betrayal"
                  :host-token token
                  :players [{:token token :name "Alex"}]}
                 (get @lobbies "ABCDEF"))))))))

(deftest normalizes-and-validates-lobby-codes
  (is (= "/lobbies/ABCDEF"
         (get-in (#'main/find-lobby "abcdef") [:headers "Location"])))
  (is (= 422 (:status (#'main/find-lobby "not a code")))))

(deftest lobby-links-can-be-shared-with-new-players
  (let [lobby {:id "ABCDEF"
               :game-name "Betrayal"
               :host-token "host-token"
               :players [{:token "host-token" :name "Alex"}]}
        host-html (#'main/render-lobby-state lobby "host-token" nil)
        guest-html (#'main/render-lobby-state lobby nil nil)]
    (is (re-find #"data-copy-url=\"/lobbies/ABCDEF\"" host-html))
    (is (re-find #">Copy link<" host-html))
    (is (re-find #"action=\"/lobbies/ABCDEF/players\"" guest-html))
    (is (re-find #"Your name" guest-html))))

(deftest local-lobby-identities-are-scoped-to-the-tab-url
  (let [lobby {:id "ABCDEF"
               :host-token "host-token"
               :players [{:token "host-token" :name "Alex"}
                         {:token "guest-token" :name "Blair"}]}
        lobbies (atom {"ABCDEF" lobby})]
    (with-redefs-fn
      {#'main/lobbies lobbies}
      (fn []
        (testing "a shared cookie does not identify a local tab"
          (is (nil? (#'main/lobby-token
                     {:remote-addr "127.0.0.1"
                      :session {:lobby-ids {"ABCDEF" "host-token"}}}
                     "ABCDEF"))))
        (testing "the tab's query token identifies its lobby participant"
          (is (= "guest-token"
                 (#'main/lobby-token
                  {:remote-addr "127.0.0.1"
                   :params {:lobby-player "guest-token"}
                   :session {:lobby-ids {"ABCDEF" "host-token"}}}
                  "ABCDEF"))))
        (testing "a local redirect carries identity in the URL, not the cookie"
          (let [response (#'main/lobby-redirect
                          {:remote-addr "127.0.0.1" :session {}}
                          "ABCDEF"
                          "guest-token")]
            (is (= "/lobbies/ABCDEF?lobby-player=guest-token"
                   (get-in response [:headers "Location"])))
            (is (nil? (:session response)))))))))

(deftest starts-a-game-and-preserves-lobby-player-bindings
  (let [host-token "host-token"
        guest-token "guest-token"
        lobby {:id "ABCDEF"
               :game-name "Friday night"
               :host-token host-token
               :players [{:token host-token :name "Alex"}
                         {:token guest-token :name "Blair"}]}
        lobbies (atom {"ABCDEF" lobby})]
    (with-redefs-fn
      {#'main/ds (delay :test-datasource)
       #'main/lobbies lobbies
       #'main/game-definitions (delay :definitions)
       #'main/broadcast-lobby! (fn [lobby-id] (is (= "ABCDEF" lobby-id)))
       #'db/create-game! (fn [datasource game-id game-name players definitions]
                           (is (= [:test-datasource "ABCDEF" "Friday night"
                                   (:players lobby) :definitions]
                                  [datasource game-id game-name players definitions]))
                           {host-token 7 guest-token 8})}
      (fn []
        (let [response (#'main/start-game
                        {:session {:lobby-ids {"ABCDEF" host-token}}}
                        "ABCDEF")]
          (is (= 204 (:status response)))
          (is (= "/lobbies/ABCDEF/enter"
                 (get-in response [:headers "HX-Redirect"])))
          (is (= {host-token 7 guest-token 8}
                 (get-in @lobbies ["ABCDEF" :player-ids])))
          (is (true? (get-in @lobbies ["ABCDEF" :started?]))))))))

(deftest entering-a-started-game-promotes-the-session-binding
  (let [lobbies
        (atom {"ABCDEF"
               {:id "ABCDEF"
                :started? true
                :player-ids {"guest-token" 8}}})]
    (with-redefs-fn
      {#'main/lobbies lobbies}
      (fn []
        (let [response (#'main/enter-game
                        {:session {:lobby-ids {"ABCDEF" "guest-token"}}}
                        "ABCDEF")]
          (is (= 303 (:status response)))
          (is (= "/games/ABCDEF" (get-in response [:headers "Location"])))
          (is (= 8 (get-in response [:session :player-ids "ABCDEF"]))))))))

(deftest local-lobby-entry-preserves-the-tab-specific-game-player
  (let [lobbies
        (atom {"ABCDEF"
               {:id "ABCDEF"
                :started? true
                :players [{:token "guest-token" :name "Blair"}]
                :player-ids {"guest-token" 8}}})]
    (with-redefs-fn
      {#'main/lobbies lobbies}
      (fn []
        (let [response
              (#'main/enter-game
               {:remote-addr "127.0.0.1"
                :params {:lobby-player "guest-token"}
                :session {}}
               "ABCDEF")]
          (is (= 303 (:status response)))
          (is (= "/games/ABCDEF?player-id=8"
                 (get-in response [:headers "Location"])))
          (is (nil? (:session response))))))))

(deftest formats-html-as-an-sse-data-message
  (is (= "data: <div>\ndata: updated\ndata: </div>\n\n"
         (#'main/sse-message "<div>\nupdated\n</div>"))))

(deftest http-commands-acknowledge-and-broadcast-authoritative-fragments
  (with-redefs-fn
    {#'main/request-player-id (fn [_ _] 7)
     #'main/run-action! (fn [game-id player-id action params]
                          (is (= ["GAME" 7 "roll" "8"]
                                 [game-id player-id action (:num-dice params)]))
                          #{:dice})
     #'main/broadcast-state! (fn [game-id regions]
                              (is (= ["GAME" #{:dice}]
                                     [game-id regions])))}
    (fn []
      (let [response (#'main/command-response
                      {:params {:num-dice "8"}} "GAME" :roll)]
        (is (= 204 (:status response)))
        (is (nil? (:body response)))))))

(deftest http-command-errors-return-an-error-fragment
  (with-redefs-fn
    {#'main/request-player-id (fn [_ _] 7)
     #'main/run-action! (fn [& _]
                          (throw (ex-info "No dice for you" {})))
     #'main/render-fragments (fn [game-id player-id error regions]
                              (is (= ["GAME" 7 "No dice for you" #{:error}]
                                     [game-id player-id error regions]))
                              "<hx-partial>error</hx-partial>")}
    (fn []
      (let [response (#'main/command-response {:params {}} "GAME" :roll)]
        (is (= 200 (:status response)))
        (is (= "<hx-partial>error</hx-partial>" (:body response)))))))

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
