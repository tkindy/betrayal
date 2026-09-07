(ns betrayal.spike.main
  (:gen-class)
  (:require [betrayal.spike.board :as board]
            [betrayal.spike.db :as db]
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
    (page
     (str (:name game) " — board spike")
     [:header.game-header
      [:a {:href "/"} "‹ Games"]
      [:strong (:name game)]
      [:span "SVG + server-rendered fragments"]]
     [:main#board-viewport {:data-game-id game-id}
      (h/raw (board/render-board (db/board @ds game-id)))])
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

(defroutes routes
  (GET "/" [] (-> (response/response (index-page))
                  (response/content-type "text/html")))
  (GET "/games/:game-id" [game-id]
    (if-let [body (board-page game-id)]
      (-> (response/response body) (response/content-type "text/html"))
      (response/not-found "Game not found")))
  (POST "/games/:game-id/move/:kind/:entity-id"
    [game-id kind entity-id :as request]
    (move request game-id kind entity-id))
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
