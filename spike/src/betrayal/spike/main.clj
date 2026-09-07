(ns betrayal.spike.main
  (:gen-class)
  (:import [java.net InetAddress]
           [java.security MessageDigest]
           [java.util UUID])
  (:require [betrayal.spike.board :as board]
            [betrayal.spike.db :as db]
            [betrayal.spike.ui :as ui]
            [clojure.string :as str]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route]
            [hiccup2.core :as h]
            [hiccup.page :refer [html5]]
            [org.httpkit.server :refer [as-channel run-server send!]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.util.response :as response]))

(defonce ^:private ds (delay (db/datasource)))

(defn- production? []
  (= "production" (System/getenv "ENVIRONMENT")))

(defn- page [title & body]
  (str
   (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title title]
     [:link {:rel "stylesheet" :href "/assets/board.css"}]
     [:script {:src "/assets/vendor/htmx-4.0.0/htmx.min.js" :defer true}]
     [:script {:src "/assets/vendor/htmx-4.0.0/hx-sse.min.js" :defer true}]
     [:script {:src "/assets/board.js" :defer true}]]
    (into [:body] body))))

(defn- local-request? [request]
  (and
   (not (production?))
   (try
     (some-> request :remote-addr InetAddress/getByName .isLoopbackAddress)
     (catch Exception _
       false))))

(defn- player-link [request game-id player]
  (if (local-request? request)
    [:a {:href (str "/games/" game-id "?player-id=" (:id player))}
     (:name player)]
    [:form {:method "post" :action (str "/games/" game-id "/player")}
     [:input {:type "hidden" :name "player-id" :value (:id player)}]
     [:button {:type "submit"} (:name player)]]))

(defn- existing-games [request]
  [:section
   [:h2 "Resume an existing game"]
   [:ul
    (for [{:keys [id name players]} (db/games @ds)]
      [:li
       [:strong name " (" id ")"]
       (if (seq players)
         [:ul
          (for [player players]
            [:li (player-link request id player)])]
         [:p [:i "No players"]])])]])

(defn- index-page [request]
  (page
   "Betrayal at House on the Hill"
   [:main.index
    [:h1 "Betrayal at House on the Hill"]
    [:div.lobby-options
     [:section
      [:h2 "New game"]
      [:form {:method "post" :action "/lobbies"}
       [:label "Your name" [:input {:name "player-name" :maxlength 20 :required true}]]
       [:button {:type "submit"} "Create lobby"]]]
     [:section
      [:h2 "Join a lobby"]
      [:form {:method "get" :action "/lobbies/join"}
       [:label "Lobby code"
        [:input {:name "lobby-id" :maxlength 6 :pattern "[A-Za-z]{6}"
                 :required true :autocapitalize "characters"}]]
       [:button {:type "submit"} "Join lobby"]]]]
    (existing-games request)]))

(defn- debug-player-id [request game-id]
  (when (local-request? request)
    (let [player-id (some-> (get-in request [:params :player-id]) parse-long)]
      (when (and player-id (db/player-in-game? @ds game-id player-id))
        player-id))))

(defn- session-player-id [request game-id]
  (let [player-id (get-in request [:session :player-ids game-id])]
    (when (and player-id (db/player-in-game? @ds game-id player-id))
      player-id)))

(defn- request-player-id [request game-id]
  (or (debug-player-id request game-id)
      (session-player-id request game-id)))

(defn- board-page [request game-id]
  (if (db/game @ds game-id)
    (let [state (db/game-state @ds game-id)
          debug-player-id (debug-player-id request game-id)
          player-id (or debug-player-id
                        (session-player-id request game-id))]
      (page
       "Betrayal"
       [:main#board-viewport
        (cond-> {:data-game-id game-id
                 :hx-sse:connect
                 (str "/games/" game-id "/events"
                      (when debug-player-id
                        (str "?player-id=" debug-player-id)))
                 :hx-swap "none"}
          debug-player-id (assoc :data-debug-player-id debug-player-id))
        (h/raw (board/render-board state game-id nil))
        (h/raw (ui/render-ui game-id state player-id nil))
        [:form#move-command
         {:hidden true
          :hx-post (str "/games/" game-id "/moves")
          :hx-swap "none"}
         [:input {:name "kind"}]
         [:input {:name "id"}]
         [:input {:name "grid-x"}]
         [:input {:name "grid-y"}]]
        [:form#place-room-command
         {:hidden true
          :hx-post (str "/games/" game-id "/actions/place-room")
          :hx-swap "none"}
         [:input {:name "grid-x"}]
         [:input {:name "grid-y"}]]]))
    nil))

(defn- parse-int [value label]
  (or (some-> value parse-long)
      (throw (ex-info (str label " must be an integer") {}))))

(defn- render-fragments-from-state
  [game-id state player-id error regions]
  (str
   (h/html
    [:div
     (when (or (contains? regions :all)
               (contains? regions :board))
       [:hx-partial {:hx-target "#board-state" :hx-swap "outerHTML"}
        (h/raw (board/render-board state game-id nil))])
     (h/raw (ui/render-updates
             game-id state player-id error regions))])))

(defn- render-fragments [game-id player-id error regions]
  (render-fragments-from-state
   game-id (db/game-state @ds game-id) player-id error regions))

(defn- run-action! [game-id player-id action params]
  (case action
    "roll"
    (do
      (db/roll-dice! @ds game-id
                     (parse-int (:num-dice params) "Number of dice")
                     (:roll-type params))
      #{:dice})

    "set-trait"
    (let [target-player-id (parse-int (:player-id params) "Player ID")]
      (db/set-trait! @ds game-id
                     target-player-id
                     (:trait params)
                     (parse-int (:index params) "Trait index"))
      #{[:traits target-player-id]})

    "add-monster"
    (do
      (db/add-monster! @ds game-id)
      #{:board})

    "rotate-room"
    (do
      (db/rotate-room! @ds game-id
                       (parse-int (:room-id params) "Room ID"))
      #{:board})

    "return-room"
    (do
      (db/return-room! @ds game-id
                       (parse-int (:room-id params) "Room ID"))
      #{:board :room-stack})

    "draw-card"
    (do
      (db/draw-card! @ds game-id
                     (parse-int (:card-type params) "Card type"))
      #{:drawn-card})

    "discard-drawn-card"
    (do
      (db/discard-drawn-card! @ds game-id)
      #{:drawn-card})

    "give-drawn-card"
    (let [target-player-id (parse-int (:player-id params) "Player ID")]
      (db/give-drawn-card! @ds game-id target-player-id)
      #{:drawn-card :dice [:inventory target-player-id]})

    "take-drawn-card"
    (if player-id
      (do
        (db/give-drawn-card! @ds game-id player-id)
        #{:drawn-card :dice [:inventory player-id]})
      (throw (ex-info "Choose which player you are before taking a card" {})))

    "discard-held-card"
    (let [target-player-id (parse-int (:player-id params) "Player ID")]
      (db/discard-held-card! @ds game-id
                             target-player-id
                             (parse-int (:card-id params) "Card ID"))
      #{:dice [:inventory target-player-id]})

    "give-held-card"
    (let [source-player-id (parse-int (:player-id params) "Player ID")
          target-player-id (parse-int (:to-player-id params) "Recipient")]
      (db/give-held-card! @ds game-id
                          source-player-id
                          (parse-int (:card-id params) "Card ID")
                          target-player-id)
      #{[:inventory source-player-id]
        [:inventory target-player-id]})

    "advance-room-stack"
    (do
      (db/advance-room-stack! @ds game-id)
      #{:board :room-stack})

    "flip-room-stack"
    (do
      (db/flip-room-stack! @ds game-id)
      #{:board :room-stack})

    "rotate-room-stack"
    (do
      (db/rotate-room-stack! @ds game-id)
      #{:board :room-stack})

    "place-room"
    (let [grid-x (parse-int (:grid-x params) "Grid X")
          grid-y (parse-int (:grid-y params) "Grid Y")
          state (board/enrich-board (db/game-state @ds game-id))]
      (when-not (some #{[grid-x grid-y]} (board/open-spots state))
        (throw (ex-info "The flipped room cannot connect at that location" {})))
      (db/place-room! @ds game-id grid-x grid-y)
      #{:board :room-stack})

    (throw (ex-info "Unknown game action" {}))))

(defn- run-move! [game-id {:keys [kind id grid-x grid-y]}]
  (let [kind (keyword kind)]
    (when-not (#{:room :player :monster} kind)
      (throw (ex-info "Unknown piece type" {})))
    (db/move! @ds game-id kind
              (parse-int id "Piece ID")
              (parse-int grid-x "Grid X")
              (parse-int grid-y "Grid Y"))
    #{:board}))

(defonce ^:private game-streams (atom {}))

(defn- sse-message [html]
  (str (str/join "\n" (map #(str "data: " %) (str/split-lines html)))
       "\n\n"))

(defn- broadcast-state! [game-id regions]
  (let [state (db/game-state @ds game-id)]
    (doseq [[channel client] @game-streams
            :when (= game-id (:game-id client))]
      (send! channel
             (sse-message
              (render-fragments-from-state
               game-id state (:player-id client) nil
               (conj regions :error)))
             false))))

(defn- game-events [request game-id]
  (if-not (db/game @ds game-id)
    (response/not-found "Game not found")
    (let [player-id (request-player-id request game-id)]
      (as-channel
       request
       {:on-open
        (fn [channel]
          (swap! game-streams assoc channel {:game-id game-id
                                             :player-id player-id})
          (send! channel
                 {:status 200
                  :headers {"Content-Type" "text/event-stream"
                            "Cache-Control" "no-cache"
                            "X-Accel-Buffering" "no"}}
                 false)
          (send! channel
                 (sse-message
                  (render-fragments game-id player-id nil #{:all}))
                 false))
        :on-close
        (fn [channel _]
          (swap! game-streams dissoc channel))}))))

(defn- command-response [request game-id command]
  (let [player-id (request-player-id request game-id)]
    (try
      (let [regions
            (case command
              :move (run-move! game-id (:params request))
              (run-action! game-id player-id (name command) (:params request)))]
        (broadcast-state! game-id regions)
        {:status 204})
      (catch Exception exception
        (-> (response/response
             (render-fragments
              game-id player-id
              (or (ex-message exception) "That action could not be completed")
              #{:error}))
            (response/content-type "text/html"))))))

(defonce ^:private lobbies (atom {}))
(defonce ^:private lobby-streams (atom {}))

(def ^:private game-definitions
  (delay
    {:characters (vals @board/character-definitions)
     :room-ids (remove #{0 1 2 8 10 33} (keys @board/room-definitions))
     :card-ids
     {0 (map (comp parse-long :id) (board/read-definitions "events.csv"))
      1 (map (comp parse-long :id) (board/read-definitions "items.csv"))
      2 (map (comp parse-long :id) (board/read-definitions "omens.csv"))}}))

(defn- valid-name [value label max-length]
  (let [value (str/trim (or value ""))]
    (when (or (str/blank? value) (> (count value) max-length))
      (throw (ex-info (format "%s must be between 1 and %d characters"
                              label max-length)
                      {})))
    value))

(defn- next-lobby-id []
  (loop []
    (let [id (apply str (repeatedly 6 #(char (+ (int \A) (rand-int 26)))))]
      (if (or (contains? @lobbies id) (db/game @ds id))
        (recur)
        id))))

(defn- lobby-member [lobby token]
  (some #(when (= token (:token %)) %) (:players lobby)))

(defn- debug-lobby-token [request lobby-id]
  (when (local-request? request)
    (let [token (get-in request [:params :lobby-player])
          lobby (get @lobbies lobby-id)]
      (when (lobby-member lobby token) token))))

(defn- lobby-token [request lobby-id]
  (if (local-request? request)
    (debug-lobby-token request lobby-id)
    (get-in request [:session :lobby-ids lobby-id])))

(defn- with-lobby-debug-token [url debug-token]
  (str url (when debug-token (str "?lobby-player=" debug-token))))

(defn- render-lobby-state [lobby token debug-token]
  (let [member (lobby-member lobby token)
        host? (= token (:host-token lobby))]
    (str
     (h/html
      [:section#lobby-state
       [:div#action-error-region.action-error {:hidden true}]
       (cond
         (:started? lobby)
         [:div
          {:data-game-url
           (with-lobby-debug-token
            (str "/lobbies/" (:id lobby) "/enter")
            debug-token)}
          [:p "Game started. Joining…"]]

         member
         [:div
          [:p.lobby-sharing
           [:button
            {:type "button"
             :data-copy-url (str "/lobbies/" (:id lobby))}
            "Copy link"]
           [:span "Lobby code: "
            [:strong (:id lobby)]]]
          [:h2 "Lobby"]
          [:ul
           (for [player (:players lobby)]
             [:li (:name player)])]
          (when host?
            (let [start-url
                  (with-lobby-debug-token
                   (str "/lobbies/" (:id lobby) "/start")
                   debug-token)]
              [:form {:method "post"
                    :action start-url
                    :hx-post start-url
                    :hx-swap "none"
                    :hx-disable "find button"}
             [:button {:type "submit"
                       :disabled (> (count (:players lobby)) 6)}
                "Start game"]]))]

         :else
         [:div
          [:h2 (str "Join " (:game-name lobby))]
          [:form {:method "post"
                  :action (str "/lobbies/" (:id lobby) "/players")}
           [:label "Your name"
            [:input {:name "player-name" :maxlength 20 :required true}]]
           [:button {:type "submit"} "Join lobby"]]])]))))

(defn- lobby-page [request lobby-id]
  (when-let [lobby (get @lobbies lobby-id)]
    (let [debug-token (debug-lobby-token request lobby-id)
          token (lobby-token request lobby-id)]
      (page
       (str (:game-name lobby) " — Lobby")
       [:main.index.lobby
        [:a {:href "/"} "‹ Home"]
        [:h1 "Game lobby"]
        [:div
         {:hx-sse:connect
          (with-lobby-debug-token
           (str "/lobbies/" lobby-id "/events")
           debug-token)
          :hx-swap "none"}
         (h/raw (render-lobby-state lobby token debug-token))]]))))

(defn- broadcast-lobby! [lobby-id]
  (when-let [lobby (get @lobbies lobby-id)]
    (doseq [[channel client] @lobby-streams
            :when (= lobby-id (:lobby-id client))]
      (send! channel
             (sse-message (str
                           (h/html
                            [:hx-partial
                             {:hx-target "#lobby-state" :hx-swap "outerHTML"}
                             (h/raw
                              (render-lobby-state
                               lobby (:token client) (:debug-token client)))])))
             false))))

(defn- lobby-redirect [request lobby-id token]
  (let [debug-token (when (local-request? request) token)]
    (cond->
     (-> (response/redirect
          (with-lobby-debug-token (str "/lobbies/" lobby-id) debug-token))
         (assoc :status 303))
      (not debug-token)
      (assoc :session
             (assoc-in (:session request) [:lobby-ids lobby-id] token)))))

(defn- create-lobby [request]
  (try
    (let [player-name (valid-name (get-in request [:params :player-name])
                                  "Player name" 20)
          lobby-id (next-lobby-id)
          token (str (UUID/randomUUID))
          player {:token token :name player-name}
          lobby {:id lobby-id
                 :game-name "Betrayal"
                 :host-token token
                 :players [player]}]
      (swap! lobbies assoc lobby-id lobby)
      (lobby-redirect request lobby-id token))
    (catch Exception exception
      (-> (response/response (ex-message exception))
          (response/status 422)))))

(defn- find-lobby [lobby-id]
  (let [lobby-id (str/upper-case (or lobby-id ""))]
    (if (re-matches #"[A-Z]{6}" lobby-id)
      (response/redirect (str "/lobbies/" lobby-id))
      (-> (response/response "Lobby code must contain six letters")
          (response/status 422)))))

(defn- join-lobby [request lobby-id]
  (try
    (let [player-name (valid-name (get-in request [:params :player-name])
                                  "Player name" 20)
          token (str (UUID/randomUUID))
          joined? (atom false)]
      (swap! lobbies
             update lobby-id
             (fn [lobby]
               (when-not lobby
                 (throw (ex-info "Lobby not found" {})))
               (when (:started? lobby)
                 (throw (ex-info "That game has already started" {})))
               (when (some #(= (str/lower-case player-name)
                               (str/lower-case (:name %)))
                           (:players lobby))
                 (throw (ex-info "That name is already in use" {})))
               (when (>= (count (:players lobby)) 6)
                 (throw (ex-info "A game can have at most 6 players" {})))
               (reset! joined? true)
               (update lobby :players conj {:token token :name player-name})))
      (when @joined? (broadcast-lobby! lobby-id))
      (lobby-redirect request lobby-id token))
    (catch Exception exception
      (-> (response/response (ex-message exception))
          (response/status 422)))))

(defn- start-game [request lobby-id]
  (try
    (let [token (lobby-token request lobby-id)
          debug-token (debug-lobby-token request lobby-id)
          lobby (get @lobbies lobby-id)]
      (when-not lobby
        (throw (ex-info "Lobby not found" {})))
      (when-not (= token (:host-token lobby))
        (throw (ex-info "Only the host can start this game" {})))
      (when-not (<= 1 (count (:players lobby)) 6)
        (throw (ex-info "A game must have between 1 and 6 players" {})))
      (let [player-ids
            (db/create-game! @ds lobby-id (:game-name lobby)
                             (:players lobby) @game-definitions)]
        (swap! lobbies assoc lobby-id
               (assoc lobby :started? true :player-ids player-ids))
        (broadcast-lobby! lobby-id)
        {:status 204
         :headers
         {"HX-Redirect"
          (with-lobby-debug-token
           (str "/lobbies/" lobby-id "/enter")
           debug-token)}}))
    (catch Exception exception
      (-> (response/response
           (str (h/html
                 [:hx-partial
                  {:hx-target "#action-error-region" :hx-swap "outerHTML"}
                  [:div#action-error-region.action-error
                   (or (ex-message exception) "The game could not be started")]])))
          (response/content-type "text/html")))))

(defn- enter-game [request lobby-id]
  (let [token (lobby-token request lobby-id)
        debug-token (debug-lobby-token request lobby-id)
        lobby (get @lobbies lobby-id)
        player-id (get-in lobby [:player-ids token])]
    (if-not player-id
      (response/not-found "Your player is not part of this game")
      (cond->
       (-> (response/redirect
            (str "/games/" lobby-id
                 (when debug-token (str "?player-id=" player-id))))
           (assoc :status 303))
        (not debug-token)
        (assoc :session
               (assoc-in (:session request)
                         [:player-ids lobby-id]
                         player-id))))))

(defn- lobby-events [request lobby-id]
  (if-let [lobby (get @lobbies lobby-id)]
    (let [token (lobby-token request lobby-id)
          debug-token (debug-lobby-token request lobby-id)]
      (as-channel
       request
       {:on-open
        (fn [channel]
          (swap! lobby-streams assoc channel
                 {:lobby-id lobby-id
                  :token token
                  :debug-token debug-token})
          (send! channel
                 {:status 200
                  :headers {"Content-Type" "text/event-stream"
                            "Cache-Control" "no-cache"
                            "X-Accel-Buffering" "no"}}
                 false)
          (send! channel
                 (sse-message
                  (str
                   (h/html
                    [:hx-partial
                     {:hx-target "#lobby-state" :hx-swap "outerHTML"}
                     (h/raw (render-lobby-state lobby token debug-token))])))
                 false))
        :on-close
        (fn [channel _]
          (swap! lobby-streams dissoc channel))}))
    (response/not-found "Lobby not found")))

(defn- select-player [request game-id]
  (let [player-id (parse-int (get-in request [:params :player-id]) "Player ID")]
    (if-not (db/player-in-game? @ds game-id player-id)
      (-> (response/response "Player is not part of this game")
          (response/status 422))
      (-> (response/redirect (str "/games/" game-id))
          (assoc :status 303)
          (assoc :session
                 (assoc-in (:session request)
                           [:player-ids game-id]
                           player-id))))))

(defroutes routes
  (GET "/up" [] (response/response "OK"))
  (GET "/" request (-> (response/response (index-page request))
                       (response/content-type "text/html")))
  (POST "/lobbies" request (create-lobby request))
  (GET "/lobbies/join" [lobby-id] (find-lobby lobby-id))
  (GET "/lobbies/:lobby-id" [lobby-id :as request]
    (if-let [body (lobby-page request lobby-id)]
      (-> (response/response body) (response/content-type "text/html"))
      (response/not-found "Lobby not found")))
  (POST "/lobbies/:lobby-id/players" [lobby-id :as request]
    (join-lobby request lobby-id))
  (POST "/lobbies/:lobby-id/start" [lobby-id :as request]
    (start-game request lobby-id))
  (GET "/lobbies/:lobby-id/enter" [lobby-id :as request]
    (enter-game request lobby-id))
  (GET "/lobbies/:lobby-id/events" [lobby-id :as request]
    (lobby-events request lobby-id))
  (GET "/games/:game-id" [game-id :as request]
    (if-let [body (board-page request game-id)]
      (-> (response/response body) (response/content-type "text/html"))
      (response/not-found "Game not found")))
  (POST "/games/:game-id/player" [game-id :as request]
    (select-player request game-id))
  (GET "/games/:game-id/events" [game-id :as request]
    (game-events request game-id))
  (POST "/games/:game-id/moves" [game-id :as request]
    (command-response request game-id :move))
  (POST "/games/:game-id/actions/:action" [game-id action :as request]
    (command-response request game-id (keyword action)))
  (route/resources "/assets" {:root "public"})
  (route/not-found "Not found"))

(defn- session-key []
  (let [secret
        (or (System/getenv "SESSION_SECRET")
            (when-not (production?)
              "betrayal-local-session-secret"))]
    (when-not secret
      (throw (ex-info "SESSION_SECRET is required in production" {})))
    (->> (.digest (MessageDigest/getInstance "SHA-256")
                  (.getBytes secret "UTF-8"))
         (take 16)
         byte-array)))

(def app
  (-> routes
      wrap-keyword-params
      wrap-params
      (wrap-session
       {:store (cookie-store {:key (session-key)})
        :cookie-name "betrayal-session"
        :cookie-attrs
        (cond-> {:http-only true :same-site :lax}
          (production?) (assoc :secure true))})))

(defn -main [& _]
  (let [port (parse-long (or (System/getenv "PORT") "8081"))]
    ;; Force configuration errors to appear before the server starts.
    @ds
    (println (str "Betrayal running at http://localhost:" port))
    (run-server #'app {:port port})))
