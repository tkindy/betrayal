(ns betrayal.spike.ui
  (:require [betrayal.spike.board :as board]
            [clojure.string :as str]
            [hiccup2.core :as h]))

(def card-types
  {0 {:key :event :label "Event" :file "events.csv"}
   1 {:key :item :label "Item" :file "items.csv"}
   2 {:key :omen :label "Omen" :file "omens.csv"}})

(def card-definitions
  (delay
    (into {}
          (for [[type-id {:keys [file]}] card-types
                definition (board/read-definitions file)]
            [[type-id (parse-long (:id definition))] definition]))))

(def trait-names
  [{:key :speed :label "Speed" :index-key :speed_index}
   {:key :might :label "Might" :index-key :might_index}
   {:key :sanity :label "Sanity" :index-key :sanity_index}
   {:key :knowledge :label "Knowledge" :index-key :knowledge_index}])

(defn- parse-trait [value]
  (let [items (str/split value #",")]
    {:values (mapv #(parse-long (str/replace % "*" "")) items)
     :starting-index (first (keep-indexed #(when (str/ends-with? %2 "*") %1)
                                          items))}))

(defn- enrich-player [player inventories]
  (let [definition (get @board/character-definitions (:character_id player))]
    (assoc player
           :character-name (:name definition)
           :color (:color definition)
           :traits (into {}
                         (for [{:keys [key]} trait-names]
                           [key (parse-trait (get definition key))]))
           :cards (mapv
                   (fn [inventory]
                     (let [type (get card-types (:card_type_id inventory))]
                       (merge (get @card-definitions
                                   [(:card_type_id inventory)
                                    (:card_def_id inventory)])
                              type
                              inventory)))
                   (filter #(= (:id player) (:player_id %)) inventories)))))

(defn prepare-state [{:keys [players inventories latest-roll] :as state}]
  (assoc state
         :players (mapv #(enrich-player % inventories) players)
         :latest-roll
         (when latest-roll
           (assoc latest-roll
                  :values (mapv parse-long (str/split (:rolls latest-roll) #"\|"))))
         :drawn-card
         (when-let [drawn (:drawn-card state)]
           (let [type (get card-types (:card_type_id drawn))]
             (merge (get @card-definitions
                         [(:card_type_id drawn) (:card_def_id drawn)])
                    type
                    drawn)))))

(defn- action-form [game-id player-id action attributes & children]
  (let [action-url
        (str "/games/" game-id "/actions/" action
             (when player-id (str "?player-id=" player-id)))]
    (into
     [:form.game-action
      (merge {:action action-url
            :method "post"
            :hx-post action-url
            :hx-swap "none"
            :hx-disable "find button"}
             attributes)]
     children)))

(defn- render-die [value]
  (let [name (case value 0 "zero" 1 "one" 2 "two" nil "zero")]
    [:img
     (cond-> {:class "die"
              :src (str "/assets/images/dice/" name ".svg")}
       (some? value) (assoc :alt name)
       (nil? value) (assoc :class "die placeholder"
                           :alt ""
                           :aria-hidden true))]))

(defn- dice-row [values]
  [:div.dice-row
   (if (seq values)
     (map render-die values)
     (render-die nil))])

(defn- dice-panel [game-id player-id {:keys [latest-roll inventories rolling?]}]
  (let [omen-count (count (filter #(= 2 (:card_type_id %)) inventories))
        values (:values latest-roll)
        total (reduce + 0 values)
        haunt? (= "HAUNT" (:type latest-roll))]
    [:section#dice-panel.panel.dice-panel {:aria-label "Dice"}
     (action-form
      game-id player-id "roll" {:class "game-action inline-form"}
      [:input {:type "number" :name "num-dice" :value 8 :min 1 :max 8
               :aria-label "Number of dice"}]
      [:input {:type "hidden" :name "roll-type" :value "AD_HOC"}]
      [:button {:type "submit"} "Roll"])
     (action-form
      game-id player-id "roll" {}
      [:input {:type "hidden" :name "num-dice" :value 6}]
      [:input {:type "hidden" :name "roll-type" :value "HAUNT"}]
      [:button.wide {:type "submit"}
       (str "Haunt roll (" omen-count " omen" (when (not= omen-count 1) "s") ")")])
     (if rolling?
       [:div.dice-result.rolling {:role "status"}
        (dice-row nil)
        (dice-row nil)
        [:strong.placeholder "Total: 0"]
        [:span.placeholder "No haunt"]
        [:strong.rolling-label "Rolling..."]]
       [:div.dice-result
        (dice-row (take 4 values))
        (dice-row (take 4 (drop 4 values)))
        [:strong {:class (when-not (seq values) "placeholder")}
         (str "Total: " total)]
        [:span
         {:class (str (when (< total omen-count) "haunt ")
                      (when-not haunt? "placeholder"))
          :aria-hidden (when-not haunt? true)}
         (if (< total omen-count) "Haunt time!" "No haunt")]])]))

(defn- draw-panel [game-id player-id]
  [:section#draw-panel.panel {:aria-label "Cards and monsters"}
   [:div.button-stack
    (for [[type-id {:keys [label]}] card-types]
      (action-form
       game-id player-id "draw-card" {}
       [:input {:type "hidden" :name "card-type" :value type-id}]
       [:button {:type "submit"} (str "Draw " (str/lower-case label))]))
    (action-form game-id player-id "add-monster" {:class "game-action monster-action"}
                 [:button.wide {:type "submit"} "Add monster"])]])

(defn search-results [locations query]
  (let [query (str/lower-case (str/trim (or query "")))
        rooms (:rooms-in-house locations)
        floor-at (when (seq rooms)
                   (into {}
                         (for [{:keys [key rooms]}
                               (board/floor-layout {:rooms (vec (vals rooms))})
                               room rooms]
                           [[(:grid_x room) (:grid_y room)] key])))
        floor-labels (into {} (map (juxt :key :label) board/floors))
        matches-name?
        (fn [value]
          (str/includes? (str/lower-case value) query))
        result-rank
        (fn [{display-name :name :keys [kind match-names]}]
          (let [display-name (str/lower-case display-name)
                match-names (map str/lower-case
                                 (or (seq match-names) [display-name]))]
            [(apply min
                    (map #(cond
                            (= % query) 0
                            (str/starts-with? % query) 1
                            :else 2)
                         match-names))
             display-name
             (clojure.core/name kind)]))]
    (if (str/blank? query)
      []
      (->>
       (concat
        (for [[definition-id definition] @board/room-definitions
              :when (matches-name? (:name definition))
              :let [room (get rooms definition-id)
                    in-stack? (contains? (:rooms-in-stack locations)
                                         definition-id)
                    floor-key (get floor-at [(:grid_x room) (:grid_y room)])
                    floor-label (get floor-labels floor-key)]]
          (cond->
           {:kind :room
            :type-label "Room"
            :definition-id definition-id
            :name (:name definition)
            :location
            (cond
              room (str "In the house"
                        (when floor-label (str " — " floor-label)))
              in-stack? "In the room stack"
              :else "Not in play")
            :pullable? in-stack?}
            room
            (assoc :floor floor-key
                   :grid-x (:grid_x room)
                   :grid-y (:grid_y room))))
        (for [player (:players locations)
              :let [character
                    (get @board/character-definitions (:character_id player))
                    player-name (:name player)
                    character-name (:name character)]
              :when (or (matches-name? player-name)
                        (matches-name? character-name))
              :let [floor-key
                    (get floor-at [(:grid_x player) (:grid_y player)])
                    floor-label (get floor-labels floor-key)]]
          {:kind :player
           :type-label "Player"
           :name (str character-name " — " player-name)
           :match-names [player-name character-name]
           :location
           (str "In the house"
                (when floor-label (str " — " floor-label)))
           :floor floor-key
           :grid-x (:grid_x player)
           :grid-y (:grid_y player)})
        (for [[[card-type-id definition-id] definition] @card-definitions
              :when (matches-name? (:name definition))
              :let [card-key [card-type-id definition-id]
                    type (get card-types card-type-id)
                    holders (get (:held-cards locations) card-key)
                    drawn? (= card-key (:drawn-card locations))
                    in-stack? (contains? (:cards-in-stacks locations) card-key)]]
          {:kind :card
           :definition-id definition-id
           :card-type-id card-type-id
           :type-label (:label type)
           :name (:name definition)
           :location
           (cond
             (seq holders)
             (str "Held by "
                  (str/join ", " (sort (distinct (map :player_name holders)))))
             drawn? "Currently drawn"
             in-stack? (str "In the " (str/lower-case (:label type)) " stack")
             :else "Not in play")
           :pullable? in-stack?}))
       (sort-by result-rank)
       (take 20)
       vec))))

(defn render-search-results [game-id player-id locations query]
  (let [results (search-results locations query)]
    (h/html
     (cond
       (str/blank? (str/trim (or query "")))
       nil

       (empty? results)
       [:p.search-empty "No matching rooms or cards"]

       :else
       [:ul.search-results-list
        (for [{:keys [kind definition-id card-type-id type-label name
                      location pullable? floor grid-x grid-y]}
              results]
          [:li.search-result
           [:div
            [:strong name]
            [:span.search-result-kind type-label]
            [:small location]]
           (when pullable?
             (action-form
              game-id player-id
              (if (= :room kind) "pull-room" "pull-card")
              {:class "game-action search-pull-action"}
              (if (= :room kind)
                [:input {:type "hidden" :name "room-definition-id"
                         :value definition-id}]
                [:span
                 [:input {:type "hidden" :name "card-type"
                          :value card-type-id}]
                 [:input {:type "hidden" :name "card-definition-id"
                          :value definition-id}]])
              [:button.danger {:type "submit"} "Pull"]))
           (when floor
             [:button
              {:type "button"
               :data-jump-location true
               :data-floor floor
               :data-grid-x grid-x
               :data-grid-y grid-y
               :aria-label (str "Jump to " name)}
              "Jump"])])]))))

(defn- search-panel [game-id player-id]
  [:section#search-panel {:aria-label "Search game pieces"}
   [:button.search-toggle
    {:type "button" :data-game-search-toggle true :aria-expanded false
     :aria-controls "search-popover"}
    "Search"]
   [:div#search-popover.panel.search-popover
    [:form.search-form
     {:action (str "/games/" game-id "/search")
      :method "get"
      :hx-get (str "/games/" game-id "/search")
      :hx-target "#search-results"
      :hx-swap "innerHTML"}
     [:input#game-search-input
      {:type "search" :name "q" :placeholder "Find a room, card, or player…"
       :aria-label "Find a room, card, or player" :autocomplete "off"
       :hx-get (str "/games/" game-id "/search")
       :hx-trigger "input changed delay:200ms, search, refreshSearch from:body"
       :hx-target "#search-results"
       :hx-swap "innerHTML"}]]
    [:div#search-results {:aria-live "polite"}]]])

(defn- zoom-panel []
  [:section#zoom-panel.panel
   [:div.zoom-actions
    [:button {:type "button" :data-board-view "zoom-out"
              :aria-label "Zoom out"} "−"]
    [:button {:type "button" :data-board-view "fit"} "Fit"]
    [:button {:type "button" :data-board-view "zoom-in"
              :aria-label "Zoom in"} "+"]]])

(defn- room-back [possible-floors]
  [:svg.room-picker-preview
   {:viewBox (str "0 0 " board/cell-size " " board/cell-size)
    :aria-label
    (str "Next room can be placed on "
         (str/join ", "
                   (for [{:keys [key label]} board/floors
                         :let [floor-code (first (str/upper-case key))]
                         :when (some #{floor-code} possible-floors)]
                     label)))}
   [:rect.picker-background {:width board/cell-size :height board/cell-size}]
   [:path.house-outline {:d "M 20 160 L 20 55 L 90 15 L 160 55 L 160 160 Z"}]
   [:path
    {:class (str "floor-window roof"
                 (when (some #{\R} possible-floors) " available"))
     :d "M 29 55 L 90 21 L 151 55 Z"}]
   (for [[index {:keys [key]}] (map-indexed vector (rest board/floors))
         :let [class key
               floor-code (first (str/upper-case key))
               top (+ 60 (* index 32))]]
     [:rect
      {:class (str "floor-window " class
                   (when (some #{floor-code} possible-floors) " available"))
       :x 29 :y top :width 122 :height 26}])])

(defn- room-stack-panel [game-id player-id room-stack]
  (let [definition (get @board/room-definitions (:room_def_id room-stack))
        flipped? (:flipped room-stack)
        room-data (when definition
                    (assoc definition
                           :doors (board/rotate-doors
                                   (:doors definition)
                                   (or (:rotation room-stack) 0))))]
    [:section#room-stack-panel.panel.room-stack-panel {:aria-label "Room stack"}
     (cond
       (nil? (:cur_index room-stack))
       [:i "Room stack empty"]

       flipped?
       [:div
        [:svg.room-picker-preview.flipped
         {:viewBox (str "0 0 " board/cell-size " " board/cell-size)
          :aria-label (str "Flipped room: " (:name definition)
                           (when (board/additional-rules? definition)
                             " — additional rules"))
          :data-room-name (:name definition)
          :data-description (or (:description definition) "")}
         (board/room-tile room-data)]
        [:p.room-placement-help "Choose a highlighted space on the board."]
        (action-form game-id player-id "rotate-room-stack" {}
                     [:button.wide {:type "submit"} "Rotate"])]

       :else
       [:div
        (room-back (:floors definition))
        [:div.split-actions
         (action-form game-id player-id "flip-room-stack" {}
                      [:button {:type "submit"} "Use"])
         (action-form game-id player-id "advance-room-stack" {}
                      [:button {:type "submit"} "Next"])]])]))

(defn- render-trait [game-id player-id player
                     {:keys [key label index-key]}]
  (let [{:keys [values starting-index]} (get-in player [:traits key])
        current-index (get player index-key)]
    [:div.trait
     [:span.trait-name label]
     [:div.trait-values
      (for [[index value] (map-indexed vector values)]
        (action-form
         game-id player-id "set-trait"
         {:class "game-action trait-form"}
         [:input {:type "hidden" :name "player-id" :value (:id player)}]
         [:input {:type "hidden" :name "trait" :value (name key)}]
         [:input {:type "hidden" :name "index" :value index}]
         [:button {:type "submit"
                   :class (str (when (= index starting-index) " starting")
                               (when (= index current-index) " active"))
                   :aria-label (str "Set " label " to " value)}
          value]))]]))

(defn- render-roll-table [value]
  [:table.roll-table
   [:tbody
    (for [row (str/split-lines value)
          :let [[_ target outcome] (re-matches #"(\S+)\s{2}(.*)" row)]]
      [:tr [:th target] [:td outcome]])]])

(defn- card-copy
  ([card] (card-copy card nil))
  ([{:keys [name subtype condition flavor-text description roll-table]}
    heading-attributes]
   [:div.card-copy
    [:h3 heading-attributes name]
    (when-not (str/blank? (or subtype condition))
      [:p.card-subtitle (or subtype condition)])
    (when-not (str/blank? flavor-text)
      [:blockquote flavor-text])
    (for [paragraph (str/split-lines description)]
      (if (= paragraph "<rollTable>")
        (render-roll-table roll-table)
        [:p paragraph]))]))

(defn- player-options [players excluded-player-id]
  (for [player players :when (not= excluded-player-id (:id player))]
    [:option {:value (:id player)}
     (str (:character-name player) " — " (:name player))]))

(defn- inventory-card [game-id player-id players player card]
  [:details.inventory-card
   {:id (str "inventory-card-" (:id card))
    :name "inventory-card"
    :hx-preserve true
    :class (name (:key card))}
   [:summary (:name card)]
   [:div.inventory-popover
    [:button.inventory-card-close
     {:type "button" :aria-label "Close card details"}
     "×"]
    (card-copy card)
    [:div.card-actions
     (action-form
      game-id player-id "give-held-card" {:class "game-action inline-form"}
      [:input {:type "hidden" :name "player-id" :value (:id player)}]
      [:input {:type "hidden" :name "card-id" :value (:id card)}]
      [:select {:name "to-player-id" :aria-label "Give card to"}
       [:option {:value ""} "Give to…"]
       (player-options players (:id player))]
      [:button {:type "submit"} "Give"])
     (action-form
      game-id player-id "discard-held-card" {}
      [:input {:type "hidden" :name "player-id" :value (:id player)}]
      [:input {:type "hidden" :name "card-id" :value (:id card)}]
      [:button.danger {:type "submit"} "Discard"])]]])

(defn- traits-panel [game-id player-id player]
  [:div.traits {:id (str "player-" (:id player) "-traits")}
   (for [trait trait-names]
     (render-trait game-id player-id player trait))])

(defn- inventory-panel [game-id player-id players player]
  (let [cards (:cards player)]
    [:div.inventory {:id (str "player-" (:id player) "-inventory")}
     [:h2 "Inventory"]
     [:div.inventory-cards
      (if (seq cards)
        (for [card cards]
          (inventory-card game-id player-id players player card))
        [:details.inventory-card.placeholder {:aria-hidden true}
         [:summary]])
      (when-not (seq cards)
        [:i.inventory-empty "Inventory empty"])]]))

(defn- character-panel [game-id player-id players]
  (let [selected-player-id
        (or (some #(when (= player-id (:id %)) player-id) players)
            (:id (first players)))]
    [:section#character-panel.panel
     [:div.character-heading
      [:label {:for "player-select"} "Viewing"]
      [:select#player-select
       (for [candidate players]
         [:option {:value (:id candidate)
                   :selected (= (:id candidate) selected-player-id)}
          (str (:character-name candidate) " — " (:name candidate))])]]
     (for [player players]
       [:div.character-content
        (cond-> {:data-viewed-player-id (:id player)}
          (not= (:id player) selected-player-id) (assoc :hidden true))
         (traits-panel game-id player-id player)
         (inventory-panel game-id player-id players player)])]))

(defn- drawn-card-overlay [game-id player-id players card]
  [:div#drawn-card-region
   (when card
     (let [heading-id (str "drawn-card-title-" (:id card))]
       [:div#drawn-card-overlay {:data-drawn-card-id (:id card)}
        [:section.drawn-card
         {:class (name (:key card))
          :aria-labelledby heading-id}
         [:button.drawn-card-view.drawn-card-minimize
          {:type "button"
           :data-drawn-card-view "minimized"
           :aria-label "Minimize drawn card"}
          "Minimize"]
         [:button.drawn-card-view.drawn-card-expand
          {:type "button"
           :data-drawn-card-view "expanded"
           :aria-label "Expand drawn card"}
          "Expand"]
         (card-copy card {:id heading-id})
         (when (= :omen (:key card))
           [:strong "Make a haunt roll now."])
         [:div.card-actions
          (when player-id
            (action-form game-id player-id "take-drawn-card" {}
                         [:button {:type "submit"} "Take"]))
          (action-form
           game-id player-id "give-drawn-card" {:class "game-action inline-form"}
           [:select {:name "player-id" :aria-label "Give card to"}
            [:option {:value ""} "Give to…"]
            (player-options players nil)]
           [:button {:type "submit"} "Give"])
          (action-form game-id player-id "discard-drawn-card"
                       {:class "game-action discard-action"}
                       [:button.danger {:type "submit"} "Discard"])]]]))])

(defn- error-region [error]
  [:div#action-error-region
   (cond-> {:class "action-error"}
     (nil? error) (assoc :hidden true))
   error])

(defn render-updates [game-id state player-id error regions]
  (let [{:keys [players drawn-card] :as state} (prepare-state state)
        player-id (when (some #(= player-id (:id %)) players) player-id)
        include? #(or (contains? regions :all) (contains? regions %))
        partial
        (fn [target content]
          (when content
            [:hx-partial {:hx-target target :hx-swap "outerHTML"}
             content]))]
    (str
     (h/html
      [:div
       (when (include? :error)
         (partial "#action-error-region" (error-region error)))
       (when (include? :dice)
         (partial "#dice-panel" (dice-panel game-id player-id state)))
       (when (include? :room-stack)
         (partial "#room-stack-panel"
                  (room-stack-panel game-id player-id (:room-stack state))))
       (when (include? :drawn-card)
         (partial "#drawn-card-region"
                  (drawn-card-overlay game-id player-id players drawn-card)))
       (for [player players
             :when (include? [:traits (:id player)])]
         (partial (str "#player-" (:id player) "-traits")
                  (traits-panel game-id player-id player)))
       (for [player players
             :when (include? [:inventory (:id player)])]
         (partial (str "#player-" (:id player) "-inventory")
                  (inventory-panel game-id player-id players player)))]))))

(defn render-ui
  ([game-id state] (render-ui game-id state nil nil))
  ([game-id state player-id error]
   (let [{:keys [players drawn-card] :as state} (prepare-state state)
         player-id (when (some #(= player-id (:id %)) players) player-id)]
     (str
      (h/html
       [:div#game-ui {:data-player-id player-id}
        (error-region error)
        (search-panel game-id player-id)
        [:aside#game-sidebar
         (zoom-panel)
         (dice-panel game-id player-id state)
         (draw-panel game-id player-id)
         (room-stack-panel game-id player-id (:room-stack state))]
        (character-panel game-id player-id players)
        (drawn-card-overlay game-id player-id players drawn-card)])))))
