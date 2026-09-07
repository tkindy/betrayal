(ns betrayal.spike.main
  (:gen-class)
  (:require [betrayal.spike.board :as board]
            [betrayal.spike.db :as db]
            [betrayal.spike.ui :as ui]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route]
            [hiccup2.core :as h]
            [hiccup.page :refer [html5]]
            [ring.adapter.jetty :refer [run-jetty]]
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

(defn- move [{:keys [params]} game-id kind entity-id]
  (let [kind (keyword kind)]
    (try
      (when-not (#{:room :player :monster} kind)
        (throw (ex-info "Unknown piece type" {})))
      (db/move! @ds game-id kind
                (parse-int entity-id "Piece ID")
                (parse-int (:grid-x params) "Grid X")
                (parse-int (:grid-y params) "Grid Y"))
      (-> (response/response (board/render-board (db/board @ds game-id)))
          (response/content-type "text/html"))
      (catch Exception exception
        (-> (response/response
             (board/render-board
              (db/board @ds game-id)
              (or (ex-message exception) "Could not move that piece")))
            (response/status 422)
            (response/content-type "text/html"))))))

(defn- selected-player-id [params]
  (some-> (:selected-player params) parse-long))

(defn- render-fragments
  ([game-id selected] (render-fragments game-id selected nil))
  ([game-id selected error]
   (let [state (db/game-state @ds game-id)]
     (str
      (h/html
       [:div#game-fragments
        (h/raw (board/render-board state))
        (h/raw (ui/render-ui game-id state selected error))])))))

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

(defn- game-action [{:keys [params]} game-id action]
  (let [selected (selected-player-id params)]
    (try
      (run-action! game-id action params)
      (-> (response/response (render-fragments game-id selected))
          (response/content-type "text/html"))
      (catch Exception exception
        (-> (response/response
             (render-fragments game-id selected
                               (or (ex-message exception)
                                   "That action could not be completed")))
            (response/status 422)
            (response/content-type "text/html"))))))

(defroutes routes
  (GET "/" [] (-> (response/response (index-page))
                  (response/content-type "text/html")))
  (GET "/games/:game-id" [game-id]
    (if-let [body (board-page game-id)]
      (-> (response/response body) (response/content-type "text/html"))
      (response/not-found "Game not found")))
  (GET "/games/:game-id/fragments" [game-id selected-player]
    (-> (response/response
         (render-fragments game-id (some-> selected-player parse-long)))
        (response/content-type "text/html")))
  (POST "/games/:game-id/move/:kind/:entity-id"
    [game-id kind entity-id :as request]
    (move request game-id kind entity-id))
  (POST "/games/:game-id/actions/:action"
    [game-id action :as request]
    (game-action request game-id action))
  (route/resources "/assets" {:root "public"})
  (route/not-found "Not found"))

(def app
  (-> routes
      wrap-keyword-params
      wrap-params))

(defn -main [& _]
  (let [port (parse-long (or (System/getenv "PORT") "8081"))]
    ;; Force configuration errors to appear before Jetty starts.
    @ds
    (println (str "Board spike running at http://localhost:" port))
    (run-jetty #'app {:port port :join? true})))
