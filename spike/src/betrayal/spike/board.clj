(ns betrayal.spike.board
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hiccup2.core :as h]))

(def cell-size 180)

(def floors
  [{:key "roof" :label "Roof" :landing-room-def-id 10}
   {:key "upper" :label "Upper" :landing-room-def-id 8}
   {:key "ground" :label "Ground" :landing-room-def-id 0}
   {:key "basement" :label "Basement" :landing-room-def-id 33}])

(defn- definition-source [resource-name]
  (or (io/resource resource-name)
      (let [directory (or (System/getenv "BETRAYAL_DEFINITIONS_DIR")
                          "../resources")
            file (io/file directory resource-name)]
        (when (.isFile file)
          file))
      (throw
       (ex-info
        (str "Could not find " resource-name
             "; run from spike/ or set BETRAYAL_DEFINITIONS_DIR")
        {}))))

(defn read-definitions [resource-name]
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

(defn- trait-value [definition trait index]
  (when (some? index)
    (some-> (get definition trait)
            (str/split #",")
            (nth index nil)
            (str/replace "*" "")
            parse-long)))

(def ^:private rotated {\E \N, \N \W, \W \S, \S \E})

(defn rotate-doors [doors rotation]
  (map (fn [door]
         (nth (iterate rotated door) (mod rotation 4)))
       doors))

(defn enrich-board [{:keys [rooms players] :as board}]
  (let [room-stack (:room-stack board)]
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
                   (let [definition
                         (get @character-definitions (:character_id player))]
                     (merge
                      player
                      {:character-name (:name definition)
                       :color (:color definition)
                       :speed (trait-value definition :speed
                                           (:speed_index player))
                       :might (trait-value definition :might
                                           (:might_index player))
                       :sanity (trait-value definition :sanity
                                            (:sanity_index player))
                       :knowledge (trait-value definition :knowledge
                                               (:knowledge_index player))})))
                 players)
           :room-stack
           (when room-stack
             (if-let [definition (get @room-definitions
                                      (:room_def_id room-stack))]
               (merge definition room-stack
                      {:doors (rotate-doors (:doors definition)
                                            (or (:rotation room-stack) 0))})
               room-stack)))))

(def ^:private direction-deltas
  {\N [0 -1], \E [1 0], \S [0 1], \W [-1 0]})

(def ^:private opposite
  {\N \S, \S \N, \E \W, \W \E})

(defn open-spots [{:keys [rooms room-stack]}]
  (if-not (:flipped room-stack)
    []
    (let [occupied (set (map (juxt :grid_x :grid_y) rooms))
          candidates
          (for [{:keys [grid_x grid_y doors]} rooms
                direction doors
                :let [[dx dy] (direction-deltas direction)
                      location [(+ grid_x dx) (+ grid_y dy)]]
                :when (not (occupied location))]
            {:location location :from direction})]
      (->> candidates
           (group-by :location)
           (keep (fn [[location neighbors]]
                   (when (some #(some #{(opposite (:from %))}
                                      (:doors room-stack))
                               neighbors)
                     location)))
           sort))))

(defn bounds [{:keys [rooms]}]
  (if (seq rooms)
    (let [xs (map :grid_x rooms)
          ys (map :grid_y rooms)]
      {:min-x (apply min xs)
       :max-x (apply max xs)
       :min-y (apply min ys)
       :max-y (apply max ys)})
    {:min-x 0 :max-x 0 :min-y 0 :max-y 0}))

(defn- neighboring-locations [[x y]]
  (map (fn [[dx dy]] [(+ x dx) (+ y dy)])
       (vals direction-deltas)))

(defn- connected-locations [occupied start claimed]
  (loop [pending (conj clojure.lang.PersistentQueue/EMPTY start)
         visited #{}]
    (if-let [location (peek pending)]
      (if (or (visited location)
              (claimed location)
              (not (occupied location)))
        (recur (pop pending) visited)
        (recur (into (pop pending) (neighboring-locations location))
               (conj visited location)))
      visited)))

(defn floor-layout
  "Assigns each contiguous group of rooms to the floor landing it contains.
  Detached groups use the nearest landing so movable rooms remain visible."
  [{:keys [rooms]}]
  (let [room-at (into {} (map (juxt (juxt :grid_x :grid_y) identity) rooms))
        occupied (set (keys room-at))
        floor-anchors
        (mapv
         (fn [floor]
           (assoc floor :anchor
                  (some (fn [room]
                          (when (= (:landing-room-def-id floor)
                                   (:room_def_id room))
                            [(:grid_x room) (:grid_y room)]))
                        rooms)))
         floors)
        [assigned claimed]
        (reduce
         (fn [[result claimed] {:keys [key anchor]}]
           (let [locations (if anchor
                             (connected-locations occupied anchor claimed)
                             #{})]
             [(into result (map #(vector % key) locations))
              (into claimed locations)]))
         [{} #{}]
         floor-anchors)
        unclaimed-components
        (loop [remaining (reduce disj occupied claimed)
               components []]
          (if-let [start (first remaining)]
            (let [component (connected-locations remaining start #{})]
              (recur (reduce disj remaining component)
                     (conj components component)))
            components))
        nearest-floor
        (fn [component]
          (:key
           (apply min-key
                  (fn [{:keys [anchor]}]
                    (if anchor
                      (apply min
                             (for [[x y] component
                                   :let [[anchor-x anchor-y] anchor]]
                               (+ (abs (- x anchor-x))
                                  (abs (- y anchor-y)))))
                      Long/MAX_VALUE))
                  floor-anchors)))
        assigned
        (reduce
         (fn [result component]
           (into result (map #(vector % (nearest-floor component)) component)))
         assigned
         unclaimed-components)]
    (mapv
     (fn [{:keys [key] :as floor}]
       (let [floor-rooms
             (filterv #(= key (get assigned [(:grid_x %) (:grid_y %)])) rooms)]
         (assoc floor
                :rooms floor-rooms
                :bounds (bounds {:rooms floor-rooms}))))
     floor-anchors)))

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

(defn additional-rules? [room]
  (not (str/blank? (:description room))))

(defn- room-rules-icon []
  [:g {:class "room-rules-icon" :aria-hidden "true"}
   [:circle {:cx 154 :cy 26 :r 12}]
   [:text {:x 154 :y 31} "i"]])

(defn room-tile [{:keys [name doors features barrier-features] :as room}]
  (let [all-features
        (str/join " " (map str (concat features barrier-features)))]
    [:g {:class "room"}
     [:rect {:class "room-background" :width cell-size :height cell-size :rx 8}]
     (map door doors)
     [:text {:class "room-name" :x (/ cell-size 2) :y 70} name]
     (when-not (str/blank? all-features)
       [:text {:class "features" :x (/ cell-size 2) :y 105} all-features])
     (when (additional-rules? room)
       (room-rules-icon))]))

(defn- grouped [entities]
  (group-by (juxt :grid_x :grid_y) entities))

(defn- token-x [index total]
  (let [spacing 32
        start (- (/ (* (dec total) spacing) 2))]
    (+ (/ cell-size 2) start (* index spacing))))

(defn- player-token [player index total]
  [:g {:class "token player draggable"
       :aria-label (str (:character-name player) " — " (:name player))
       :data-kind "player" :data-id (:id player)
       :data-grid-x (:grid_x player) :data-grid-y (:grid_y player)
       :data-player-name (:name player)
       :data-character-name (:character-name player)
       :data-speed (:speed player)
       :data-might (:might player)
       :data-sanity (:sanity player)
       :data-knowledge (:knowledge player)
       :transform (format "translate(%s %d)" (token-x index total) 137)}
   [:circle {:r 14 :fill (str/lower-case (:color player))}]])

(defn- monster-token [monster index total]
  [:g {:class "token monster draggable"
       :data-kind "monster" :data-id (:id monster)
       :data-grid-x (:grid_x monster) :data-grid-y (:grid_y monster)
       :transform (format "translate(%s %d)" (token-x index total) 137)}
   [:rect {:x -13 :y -13 :width 26 :height 26 :rx 4}]
   [:text {:y 5} (:number monster)]
   [:title (str "Monster " (:number monster) " — drag to another room")]])

(defn- minimap [{:keys [key label rooms bounds]}]
  (let [{:keys [min-x max-x min-y max-y]} bounds
        padding 0.3
        width (+ 1 (- max-x min-x) (* 2 padding))
        height (+ 1 (- max-y min-y) (* 2 padding))]
    [:button.floor-select
     {:type "button"
      :data-floor-select key
      :data-min-x min-x :data-max-x max-x
      :data-min-y min-y :data-max-y max-y
      :aria-label (str "View " label " floor")}
     [:span.floor-label label]
     [:svg.floor-minimap
      {:viewBox (format "%s %s %s %s"
                        (- min-x padding) (- min-y padding) width height)
       :aria-hidden "true"}
      (for [{:keys [grid_x grid_y]} rooms]
        [:rect {:x grid_x :y grid_y :width 1 :height 1 :rx 0.08}])]]))

(defn render-board
  ([board] (render-board board nil nil))
  ([board error] (render-board board nil error))
  ([board game-id _error]
   (let [board (enrich-board board)
         players (grouped (:players board))
         monsters (grouped (:monsters board))
         layout (floor-layout board)
         floor-at
         (into {}
               (for [{:keys [key rooms]} layout
                     room rooms]
                 [[(:grid_x room) (:grid_y room)] key]))
         open-spots-by-floor
         (group-by
          (fn [[grid-x grid-y]]
            (some (fn [[dx dy]]
                    (get floor-at [(- grid-x dx) (- grid-y dy)]))
                  (vals direction-deltas)))
          (open-spots board))]
     (str
      (h/html
       [:div#board-state
        [:nav#floor-navigation.panel {:aria-label "Floors"}
         [:button#floor-drawer-toggle
          {:type "button"
           :aria-expanded "false"
           :aria-controls "floor-navigation"}
          "Floors"]
         (map minimap (reverse layout))]
        [:svg#board {:aria-label "Betrayal game board"}
         [:g#world
          (for [{:keys [key rooms]} layout]
            [:g.floor-canvas {:data-floor key}
             [:g.rooms
              (for [[grid-x grid-y] (get open-spots-by-floor key)]
                [:g.open-spot
                 {:data-grid-x grid-x
                  :data-grid-y grid-y
                  :transform (format "translate(%d %d)"
                                     (* grid-x cell-size) (* grid-y cell-size))}
                 [:rect {:width cell-size :height cell-size :rx 8}]
                 [:text {:x (/ cell-size 2) :y (/ cell-size 2)} "Place room"]])
              (for [room-data rooms]
                [:g {:class "room-cell draggable"
                     :aria-label (str (:name room-data)
                                      (when (additional-rules? room-data)
                                        " — additional rules"))
                     :data-kind "room" :data-id (:id room-data)
                     :data-grid-x (:grid_x room-data)
                     :data-grid-y (:grid_y room-data)
                     :data-room-name (:name room-data)
                     :data-description (or (:description room-data) "")
                     :transform (format "translate(%d %d)"
                                        (* (:grid_x room-data) cell-size)
                                        (* (:grid_y room-data) cell-size))}
                 (room-tile room-data)])]
             [:g.tokens
              (for [room-data rooms
                    :let [loc [(:grid_x room-data) (:grid_y room-data)]
                          room-players (get players loc)
                          room-monsters (get monsters loc)
                          player-count (count room-players)
                          token-count (+ player-count (count room-monsters))]
                    :when (or (seq room-players) (seq room-monsters))]
                [:g {:class "agents"
                     :data-grid-x (:grid_x room-data)
                     :data-grid-y (:grid_y room-data)
                     :transform (format "translate(%d %d)"
                                        (* (:grid_x room-data) cell-size)
                                        (* (:grid_y room-data) cell-size))}
                 (map-indexed
                  (fn [index player]
                    (player-token player index token-count))
                  room-players)
                 (map-indexed
                  (fn [index monster]
                    (monster-token monster (+ player-count index) token-count))
                  room-monsters)])]])]]
        [:svg#drag-overlay {:aria-hidden "true"}
         [:g#drag-layer]]
        [:div#room-details
         {:role "dialog" :aria-hidden "true" :hidden true}
         [:strong.room-details-name]
         [:div.room-details-description]
         [:details.room-actions-menu
          [:summary {:aria-label "Room actions"} "⋯"]
          [:div.room-details-actions
           [:form.game-action
            {:action (str "/games/" game-id "/actions/rotate-room")
             :method "post"
             :hx-post (str "/games/" game-id "/actions/rotate-room")
             :hx-swap "none"
             :hx-disable "find button"}
            [:input.room-details-id
             {:type "hidden" :name "room-id"}]
            [:button {:type "submit"} "Rotate"]]
           [:form.game-action
            {:action (str "/games/" game-id "/actions/return-room")
             :method "post"
             :hx-post (str "/games/" game-id "/actions/return-room")
             :hx-swap "none"
             :hx-disable "find button"
             :hx-confirm "Return this room to the stack?"}
            [:input.room-details-id
             {:type "hidden" :name "room-id"}]
            [:button.danger {:type "submit"} "Return to stack"]]]]]
        [:div#player-details
         {:role "dialog" :aria-hidden "true" :hidden true}
         [:strong.player-details-character]
         [:span.player-details-name]
         [:dl.player-details-traits
          [:div [:dt "Speed"] [:dd.player-details-speed]]
          [:div [:dt "Might"] [:dd.player-details-might]]
          [:div [:dt "Sanity"] [:dd.player-details-sanity]]
          [:div [:dt "Knowledge"] [:dd.player-details-knowledge]]]]])))))
