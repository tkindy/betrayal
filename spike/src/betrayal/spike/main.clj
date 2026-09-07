(ns betrayal.spike.main
  (:gen-class)
  (:require [betrayal.spike.board :as board]
            [betrayal.spike.db :as db]
            [betrayal.spike.ui :as ui]
            [clojure.data.json :as json]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :as route]
            [hiccup2.core :as h]
            [hiccup.page :refer [html5]]
            [org.httpkit.server :refer [as-channel run-server send!]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as response]))

(defonce ^:private ds (delay (db/datasource)))

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

(defn- index-page []
  (page
   "Betrayal board spike"
   [:main.index
    [:h1 "Betrayal board spike"]
    [:p "Choose an existing game from the configured database."]
    [:ul
     (for [{:keys [id name]} (db/games @ds)]
       [:li [:a {:href (str "/games/" id)} name " (" id ")"]])]]))

(defn- board-page [game-id]
  (if-let [game (db/game @ds game-id)]
    (let [state (db/game-state @ds game-id)]
      (page
       (str (:name game) " — board spike")
       [:header.game-header
        [:a {:href "/"} "‹ Games"]
        [:strong (:name game)]
        [:span "SVG + server-rendered fragments"]]
       [:main#board-viewport {:data-game-id game-id}
        (h/raw (board/render-board state))
        (h/raw (ui/render-ui game-id state))]))
    nil))

(defn- parse-int [value label]
  (or (some-> value parse-long)
      (throw (ex-info (str label " must be an integer") {}))))

(defn- selected-player-id [params]
  (some-> (:selected-player params) parse-long))

(defn- render-fragments-from-state [game-id state selected error]
  (str
   (h/html
    [:div#game-fragments
     (h/raw (board/render-board state))
     (h/raw (ui/render-ui game-id state selected error))])))

(defn- render-fragments [game-id selected error]
  (render-fragments-from-state
   game-id (db/game-state @ds game-id) selected error))

(defn- run-action! [game-id action params]
  (case action
    "roll"
    (db/roll-dice! @ds game-id
                   (parse-int (:num-dice params) "Number of dice")
                   (:roll-type params))

    "set-trait"
    (db/set-trait! @ds game-id
                   (parse-int (:player-id params) "Player ID")
                   (:trait params)
                   (parse-int (:index params) "Trait index"))

    "add-monster"
    (db/add-monster! @ds game-id)

    "draw-card"
    (db/draw-card! @ds game-id
                   (parse-int (:card-type params) "Card type"))

    "discard-drawn-card"
    (db/discard-drawn-card! @ds game-id)

    "give-drawn-card"
    (db/give-drawn-card! @ds game-id
                         (parse-int (:player-id params) "Player ID"))

    "discard-held-card"
    (db/discard-held-card! @ds game-id
                           (parse-int (:player-id params) "Player ID")
                           (parse-int (:card-id params) "Card ID"))

    "give-held-card"
    (db/give-held-card! @ds game-id
                        (parse-int (:player-id params) "Player ID")
                        (parse-int (:card-id params) "Card ID")
                        (parse-int (:to-player-id params) "Recipient"))

    "advance-room-stack"
    (db/advance-room-stack! @ds game-id)

    "flip-room-stack"
    (db/flip-room-stack! @ds game-id)

    "rotate-room-stack"
    (db/rotate-room-stack! @ds game-id)

    "place-room"
    (let [grid-x (parse-int (:grid-x params) "Grid X")
          grid-y (parse-int (:grid-y params) "Grid Y")
          state (board/enrich-board (db/game-state @ds game-id))]
      (when-not (some #{[grid-x grid-y]} (board/open-spots state))
        (throw (ex-info "The flipped room cannot connect at that location" {})))
      (db/place-room! @ds game-id grid-x grid-y))

    (throw (ex-info "Unknown game action" {}))))

(defn- run-move! [game-id {:keys [kind id grid-x grid-y]}]
  (let [kind (keyword kind)]
    (when-not (#{:room :player :monster} kind)
      (throw (ex-info "Unknown piece type" {})))
    (db/move! @ds game-id kind
              (parse-int id "Piece ID")
              (parse-int grid-x "Grid X")
              (parse-int grid-y "Grid Y"))))

(defonce ^:private clients (atom {}))

(defn- send-state! [channel error]
  (when-let [{:keys [game-id selected-player]} (get @clients channel)]
    (send! channel (render-fragments game-id selected-player error))))

(defn- broadcast-state! [game-id]
  (let [state (db/game-state @ds game-id)]
    (doseq [[channel client] @clients
            :when (= game-id (:game-id client))]
      (send! channel
             (render-fragments-from-state
              game-id state (:selected-player client) nil)))))

(defn- remember-selected-player! [channel params]
  (when-let [selected (selected-player-id params)]
    (swap! clients assoc-in [channel :selected-player] selected)))

(defn- receive-command! [channel message]
  (try
    (let [{:keys [command action] :as params}
          (json/read-str message :key-fn keyword)
          game-id (get-in @clients [channel :game-id])]
      (remember-selected-player! channel params)
      (case command
        "select-player"
        (send-state! channel nil)

        "move"
        (do
          (run-move! game-id params)
          (broadcast-state! game-id))

        "action"
        (do
          (run-action! game-id action params)
          (broadcast-state! game-id))

        (throw (ex-info "Unknown WebSocket command" {}))))
    (catch Exception exception
      (send-state! channel
                   (or (ex-message exception)
                       "That command could not be completed")))))

(defn- game-websocket [request game-id]
  (if-not (db/game @ds game-id)
    (response/not-found "Game not found")
    (let [initial-player (selected-player-id (:params request))]
      (as-channel
       request
       {:on-open
        (fn [channel]
          (swap! clients assoc channel {:game-id game-id
                                        :selected-player initial-player})
          (send-state! channel nil))
        :on-receive
        (fn [channel message]
          (receive-command! channel message))
        :on-close
        (fn [channel _]
          (swap! clients dissoc channel))}))))

(defroutes routes
  (GET "/" [] (-> (response/response (index-page))
                  (response/content-type "text/html")))
  (GET "/games/:game-id" [game-id]
    (if-let [body (board-page game-id)]
      (-> (response/response body) (response/content-type "text/html"))
      (response/not-found "Game not found")))
  (GET "/games/:game-id/socket" [game-id :as request]
    (game-websocket request game-id))
  (route/resources "/assets" {:root "public"})
  (route/not-found "Not found"))

(def app
  (-> routes
      wrap-keyword-params
      wrap-params))

(defn -main [& _]
  (let [port (parse-long (or (System/getenv "PORT") "8081"))]
    ;; Force configuration errors to appear before the server starts.
    @ds
    (println (str "Board spike running at http://localhost:" port))
    (run-server #'app {:port port})))
