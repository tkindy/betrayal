(ns betrayal.spike.main
  (:gen-class)
  (:import [java.net InetAddress]
           [java.security MessageDigest])
  (:require [betrayal.spike.board :as board]
            [betrayal.spike.db :as db]
            [betrayal.spike.ui :as ui]
            [clojure.data.json :as json]
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

(defn- index-page [request]
  (page
   "Betrayal board spike"
   [:main.index
    [:h1 "Betrayal board spike"]
    [:p "Choose who to play as in an existing game."]
    [:ul
     (for [{:keys [id name players]} (db/games @ds)]
       [:li
        [:strong name " (" id ")"]
        (if (seq players)
          [:ul
           (for [player players]
             [:li (player-link request id player)])]
          [:p [:i "No players"]])])]]))

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
  (if-let [game (db/game @ds game-id)]
    (let [state (db/game-state @ds game-id)
          debug-player-id (debug-player-id request game-id)
          player-id (or debug-player-id
                        (session-player-id request game-id))]
      (page
       (str (:name game) " — board spike")
       [:header.game-header
        [:a {:href "/"} "‹ Games"]
        [:strong (:name game)]
        [:span "SVG + server-rendered fragments"]]
       [:main#board-viewport
        (cond-> {:data-game-id game-id}
          debug-player-id (assoc :data-debug-player-id debug-player-id))
        (h/raw (board/render-board state))
        (h/raw (ui/render-ui game-id state player-id nil))]))
    nil))

(defn- parse-int [value label]
  (or (some-> value parse-long)
      (throw (ex-info (str label " must be an integer") {}))))

(defn- render-fragments-from-state
  [game-id state player-id error regions]
  (str
   (h/html
    [:div#game-fragments
     (when (or (contains? regions :all)
               (contains? regions :board))
       (h/raw (board/render-board state)))
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

(defonce ^:private clients (atom {}))

(defn- send-message! [channel message]
  (send! channel (json/write-str message)))

(defn- send-state! [channel message-type error regions command-id]
  (when-let [{:keys [game-id player-id]} (get @clients channel)]
    (send-message!
     channel
     (cond-> {:type message-type
              :html (render-fragments game-id player-id error regions)}
       command-id (assoc :id command-id)
       error (assoc :message error)))))

(defn- broadcast-state! [game-id regions]
  (let [state (db/game-state @ds game-id)]
    (doseq [[channel client] @clients
            :when (= game-id (:game-id client))]
      (send! channel
             (json/write-str
              {:type "update"
               :html
               (render-fragments-from-state
                game-id state (:player-id client) nil
                (conj regions :error))})))))

(defn- receive-command! [channel message]
  (let [command-id (atom nil)]
    (try
      (let [{:keys [id command action] :as params}
            (json/read-str message :key-fn keyword)
            {:keys [game-id player-id]} (get @clients channel)]
        (reset! command-id id)
        (when-not (and (string? id) (<= 1 (count id) 100))
          (throw (ex-info "Every command must have an ID" {})))
        (let [regions
              (case command
                "move"
                (run-move! game-id params)

                "action"
                (run-action! game-id player-id action params)

                (throw (ex-info "Unknown WebSocket command" {})))]
          (broadcast-state! game-id regions)
          (send-message! channel {:type "ack" :id id})))
      (catch Exception exception
        (send-state! channel
                     "error"
                     (or (ex-message exception)
                         "That command could not be completed")
                     #{:all}
                     @command-id)))))

(defn- game-websocket [request game-id]
  (if-not (db/game @ds game-id)
    (response/not-found "Game not found")
    (let [player-id (request-player-id request game-id)]
      (as-channel
       request
       {:on-open
        (fn [channel]
          (swap! clients assoc channel {:game-id game-id
                                        :player-id player-id})
          (send-state! channel "state" nil #{:all} nil))
        :on-receive
        (fn [channel message]
          (receive-command! channel message))
        :on-close
        (fn [channel _]
          (swap! clients dissoc channel))}))))

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
  (GET "/games/:game-id" [game-id :as request]
    (if-let [body (board-page request game-id)]
      (-> (response/response body) (response/content-type "text/html"))
      (response/not-found "Game not found")))
  (POST "/games/:game-id/player" [game-id :as request]
    (select-player request game-id))
  (GET "/games/:game-id/socket" [game-id :as request]
    (game-websocket request game-id))
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
    (println (str "Board spike running at http://localhost:" port))
    (run-server #'app {:port port})))
