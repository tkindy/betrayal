(ns betrayal.spike.board
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hiccup2.core :as h]))

(def cell-size 180)

(defn- definition-source [resource-name]
  (or (io/resource resource-name)
      (let [directory (or (System/getenv "BETRAYAL_DEFINITIONS_DIR")
                          "../api/src/main/resources")
            file (io/file directory resource-name)]
        (when (.isFile file)
          file))
      (throw
       (ex-info
        (str "Could not find " resource-name
             "; run from spike/ or set BETRAYAL_DEFINITIONS_DIR")
        {}))))

(defn- read-definitions [resource-name]
  (with-open [reader (io/reader (definition-source resource-name))]
    (let [[headers & rows] (csv/read-csv reader)
          keys (mapv (comp keyword str/lower-case
                           #(str/replace % #"([a-z])([A-Z])" "$1-$2"))
                     headers)]
      (mapv #(zipmap keys %) rows))))

(def room-definitions
  (delay (into {} (map (juxt (comp parse-long :id) identity)
                       (read-definitions "rooms.csv")))))

(def character-definitions
  (delay (into {} (map (juxt (comp parse-long :id) identity)
                       (read-definitions "characters.csv")))))

(def ^:private clockwise {\N \E, \E \S, \S \W, \W \N})

(defn rotate-doors [doors rotation]
  (map (fn [door]
         (nth (iterate clockwise door) (mod rotation 4)))
       doors))

(defn enrich-board [{:keys [rooms players] :as board}]
  (assoc board
         :rooms
         (mapv (fn [room]
                 (let [definition (get @room-definitions (:room_def_id room))]
                   (merge definition room
                          {:doors (rotate-doors (:doors definition)
                                                (:rotation room))})))
               rooms)
         :players
         (mapv (fn [player]
                 (merge player
                        (select-keys
                         (get @character-definitions (:character_id player))
                         [:color])))
               players)))

(defn bounds [{:keys [rooms]}]
  (if (seq rooms)
    (let [xs (map :grid_x rooms)
          ys (map :grid_y rooms)]
      {:min-x (apply min xs)
       :max-x (apply max xs)
       :min-y (apply min ys)
       :max-y (apply max ys)})
    {:min-x 0 :max-x 0 :min-y 0 :max-y 0}))

(defn- door [direction]
  (let [half (/ cell-size 2)
        gap 28
        points (case direction
                 \N [[(- half gap) 1] [(+ half gap) 1]]
                 \S [[(- half gap) (dec cell-size)]
                     [(+ half gap) (dec cell-size)]]
                 \W [[1 (- half gap)] [1 (+ half gap)]]
                 \E [[(dec cell-size) (- half gap)]
                     [(dec cell-size) (+ half gap)]])]
    [:line {:class "door"
            :x1 (ffirst points) :y1 (second (first points))
            :x2 (first (second points)) :y2 (second (second points))}]))

(defn- room [{:keys [name doors features description]}]
  [:g {:class "room"}
   [:rect {:class "room-background" :width cell-size :height cell-size :rx 8}]
   (map door doors)
   [:text {:class "room-name" :x (/ cell-size 2) :y 70} name]
   (when-not (str/blank? features)
     [:text {:class "features" :x (/ cell-size 2) :y 105} features])
   (when-not (str/blank? description)
     [:title description])])

(defn- grouped [entities]
  (group-by (juxt :grid_x :grid_y) entities))

(defn- player-token [player index total]
  (let [spacing 32
        start (- (/ (* (dec total) spacing) 2))
        x (+ (/ cell-size 2) start (* index spacing))]
    [:g {:class "token player draggable"
         :data-kind "player" :data-id (:id player)
         :data-grid-x (:grid_x player) :data-grid-y (:grid_y player)
         :transform (format "translate(%s %d)" x 137)}
     [:circle {:r 14 :fill (str/lower-case (:color player))}]
     [:title (str (:name player) " — drag to another room")]]))

(defn- monster-token [index monster]
  [:g {:class "token monster draggable"
       :data-kind "monster" :data-id (:id monster)
       :data-grid-x (:grid_x monster) :data-grid-y (:grid_y monster)
       :transform (format "translate(%d %d)" (+ 20 (* index 28)) 20)}
   [:rect {:x -13 :y -13 :width 26 :height 26 :rx 4}]
   [:text {:y 5} (:number monster)]
   [:title (str "Monster " (:number monster) " — drag to another room")]])

(defn render-board
  ([board] (render-board board nil))
  ([board error]
   (let [board (enrich-board board)
         players (grouped (:players board))
         monsters (grouped (:monsters board))
         {:keys [min-x max-x min-y max-y]} (bounds board)]
     (str
      (h/html
       [:div#board-state
        {:data-min-x min-x :data-max-x max-x
         :data-min-y min-y :data-max-y max-y}
        [:div.status {:class (when error "error")}
         (or error "Drag rooms, players, and monsters. Drag empty space to pan; scroll to zoom.")]
        [:svg#board {:aria-label "Betrayal game board"}
         [:g#world
          [:g.rooms
           (for [room-data (:rooms board)]
             [:g {:class "room-cell draggable"
                  :data-kind "room" :data-id (:id room-data)
                  :data-grid-x (:grid_x room-data)
                  :data-grid-y (:grid_y room-data)
                  :transform (format "translate(%d %d)"
                                     (* (:grid_x room-data) cell-size)
                                     (* (:grid_y room-data) cell-size))}
              (room room-data)])]
          [:g.tokens
           (for [room-data (:rooms board)
                 :let [loc [(:grid_x room-data) (:grid_y room-data)]
                       room-players (get players loc)
                       room-monsters (get monsters loc)]
                 :when (or (seq room-players) (seq room-monsters))]
             [:g {:class "agents"
                  :data-grid-x (:grid_x room-data)
                  :data-grid-y (:grid_y room-data)
                  :transform (format "translate(%d %d)"
                                     (* (:grid_x room-data) cell-size)
                                     (* (:grid_y room-data) cell-size))}
              (map-indexed
               (fn [index player]
                 (player-token player index (count room-players)))
               room-players)
              (map-indexed monster-token room-monsters)])]]]])))))
