(ns betrayal.spike.ui-test
  (:require [betrayal.spike.ui :as ui]
            [clojure.test :refer [deftest is testing]]))

(def state
  {:rooms []
   :players [{:id 7
              :name "Alex"
              :character_id 0
              :grid_x 4
              :grid_y 3
              :speed_index 2
              :might_index 2
              :sanity_index 2
              :knowledge_index 2}]
   :monsters []
   :inventories [{:id 42 :player_id 7 :card_type_id 1 :card_def_id 0}]
   :drawn-card nil
   :latest-roll {:id 3 :rolls "0|1|2" :type "AD_HOC"}
   :room-stack {:id 2 :cur_index 0 :flipped false :rotation nil
                :content_id 9 :room_def_id 3}})

(deftest prepares-player-state
  (let [prepared (ui/prepare-state state)
        player (first (:players prepared))
        card (first (:cards player))]
    (is (= "Ox Bellows" (:character-name player)))
    (is (= [2 2 2 3 4 5 5 6] (get-in player [:traits :speed :values])))
    (is (= 42 (:id card)) "inventory ID wins over the definition ID")
    (is (= "Chainsaw" (:name card)))))

(deftest renders-game-controls
  (let [html (ui/render-ui "ABC123" state 7 nil)]
    (testing "dice, card, monster, trait, and inventory actions are present"
      (is (re-find #"/actions/roll\?player-id=7" html))
      (is (re-find #"/actions/draw-card\?player-id=7" html))
      (is (re-find #"/actions/add-monster\?player-id=7" html))
      (is (re-find #"/actions/flip-room-stack\?player-id=7" html))
      (is (re-find #"/actions/set-trait\?player-id=7" html))
      (is (re-find #"/actions/discard-held-card\?player-id=7" html))
      (is (re-find #"data-board-view=\"zoom-in\"" html))
      (is (re-find #"data-board-view=\"fit\"" html))
      (is (re-find #"hx-post=\"/games/ABC123/actions/roll\?player-id=7\"" html))
      (is (re-find #"hx-disable=\"find button\"" html))
      (is (= 2 (count (re-seq #"class=\"dice-row\"" html)))
          "dice results always reserve two rows")
      (is (re-find #"/assets/images/dice/zero.svg" html))
      (is (re-find #"/assets/images/dice/one.svg" html))
      (is (re-find #"/assets/images/dice/two.svg" html))
      (is (not (re-find #"<h2>(Board view|Dice|Cards|Room stack)</h2>" html)))
      (is (< (.indexOf html "id=\"game-sidebar\"")
             (.indexOf html "id=\"zoom-panel\"")
             (.indexOf html "id=\"dice-panel\""))
          "zoom controls render first within the sidebar"))
    (testing "the latest roll and held card are rendered"
      (is (re-find #"Total: 3" html))
      (is (re-find #"Chainsaw" html)))))

(deftest renders-rolling-placeholder-instead-of-the-dice-result
  (let [html (ui/render-ui "ABC123" (assoc state :rolling? true) 7 nil)]
    (is (re-find #">Rolling\.\.\.<" html))
    (is (re-find #"role=\"status\"" html))
    (is (not (re-find #"Total: 3" html)))
    (is (= 2 (count (re-seq #"class=\"dice-row\"" html)))
        "hidden dice rows preserve the result area's normal height")
    (is (= 2 (count (re-seq #"class=\"die placeholder\"" html))))))

(deftest renders-empty-inventory-with-a-card-sized-placeholder
  (let [html (ui/render-ui "ABC123" (assoc state :inventories []) 7 nil)]
    (is (re-find #"class=\"inventory-cards\"" html))
    (is (re-find #"aria-hidden=\"aria-hidden\" class=\"inventory-card placeholder\"" html))
    (is (re-find #"class=\"inventory-empty\">Inventory empty<" html))))

(deftest reuses-board-room-rendering-in-picker
  (let [flipped (assoc-in state [:room-stack :flipped] true)
        html (ui/render-ui "ABC123" flipped 7 nil)]
    (is (re-find #"room-picker-preview flipped" html))
    (is (re-find #"class=\"room\"" html))
    (is (re-find #">Nursery<" html))
    (is (re-find #"aria-label=\"Flipped room: Nursery — additional rules\"" html))
    (is (re-find #"data-room-name=\"Nursery\"" html))
    (is (re-find #"data-description=\"If you end your turn here" html))
    (is (re-find #"class=\"room-rules-icon\"" html))
    (is (re-find #"class=\"door\"" html))))

(deftest renders-acting-player-actions
  (let [drawn-state (assoc state :drawn-card
                           {:id 10 :card_type_id 1 :card_def_id 0})
        identified-html (ui/render-ui "ABC123" drawn-state 7 nil)
        anonymous-html (ui/render-ui "ABC123" drawn-state nil nil)]
    (testing "the acting player can take a drawn card directly"
      (is (re-find #"/actions/take-drawn-card\?player-id=7" identified-html))
      (is (re-find #">Take<" identified-html))
      (is (< (.indexOf identified-html "take-drawn-card")
             (.indexOf identified-html "give-drawn-card")
             (.indexOf identified-html "discard-drawn-card"))
          "take and give precede the right-aligned discard action"))
    (testing "an unidentified tab cannot take the card directly"
      (is (not (re-find #"/actions/take-drawn-card" anonymous-html)))
      (is (re-find #">Viewing<" anonymous-html))
      (is (not (re-find #"Playing as" anonymous-html))))))

(deftest searches-rooms-and-cards-by-name-and-location
  (let [locations
        {:rooms-in-house
         {0 {:room_def_id 0 :grid_x 4 :grid_y 3}}
         :players [{:name "Alex" :character_id 0 :grid_x 4 :grid_y 3}]
         :rooms-in-stack #{3}
         ;; Search must not expose whether a stacked room is the current tile.
         :current-room {:room_def_id 3 :flipped false}
         :held-cards
         {[1 0] [{:card_type_id 1 :card_def_id 0 :player_name "Alex"}]}
         :drawn-card nil
         :cards-in-stacks #{[2 0]}}
        room (first (ui/search-results locations " nursery "))
        house-room (first (ui/search-results locations "Entrance Hall"))
        player (first (ui/search-results locations "alex"))
        character (first (ui/search-results locations "Ox Bellows"))
        card (first (ui/search-results locations "Chainsaw"))]
    (testing "stacked rooms have the same status and action when currently on top"
      (is (= {:kind :room
              :type-label "Room"
              :definition-id 3
              :name "Nursery"
              :location "In the room stack"
              :pullable? true}
             room)))
    (testing "rooms in the house include a client-side jump target"
      (is (= "In the house — Ground" (:location house-room)))
      (is (= {:floor "ground" :grid-x 4 :grid-y 3}
             (select-keys house-room [:floor :grid-x :grid-y]))))
    (testing "players can be found by player or character name"
      (is (= player character))
      (is (= "Ox Bellows — Alex" (:name player)))
      (is (= "Player" (:type-label player)))
      (is (= "In the house — Ground" (:location player)))
      (is (= {:floor "ground" :grid-x 4 :grid-y 3}
             (select-keys player [:floor :grid-x :grid-y]))))
    (testing "held cards report their owner and cannot be pulled"
      (is (= "Chainsaw" (:name card)))
      (is (= "Held by Alex" (:location card)))
      (is (false? (:pullable? card))))))

(deftest renders-search-controls-and-pull-actions
  (let [html (ui/render-ui "ABC123" state 7 nil)
        results-html
        (str
         (ui/render-search-results
          "ABC123" 7
          {:rooms-in-house {}
           :players []
           :rooms-in-stack #{3}
           :held-cards {}
           :drawn-card nil
           :cards-in-stacks #{}}
          "Nursery"))
        card-results-html
        (str
         (ui/render-search-results
          "ABC123" 7
          {:rooms-in-house {}
           :players []
           :rooms-in-stack #{}
           :held-cards {}
           :drawn-card nil
           :cards-in-stacks #{[2 0]}}
          "Bite"))
        house-results-html
        (str
         (ui/render-search-results
          "ABC123" 7
          {:rooms-in-house
           {0 {:room_def_id 0 :grid_x 4 :grid_y 3}}
           :players []
           :rooms-in-stack #{}
           :held-cards {}
           :drawn-card nil
           :cards-in-stacks #{}}
          "Entrance Hall"))
        player-results-html
        (str
         (ui/render-search-results
          "ABC123" 7
          {:rooms-in-house
           {0 {:room_def_id 0 :grid_x 4 :grid_y 3}}
           :players [{:name "Alex" :character_id 0 :grid_x 4 :grid_y 3}]
           :rooms-in-stack #{}
           :held-cards {}
           :drawn-card nil
           :cards-in-stacks #{}}
          "Alex"))]
    (is (re-find #"id=\"game-search-input\"" html))
    (is (re-find #"data-game-search-toggle" html))
    (is (re-find #"id=\"search-popover\"" html))
    (is (< (.indexOf html "id=\"search-panel\"")
           (.indexOf html "id=\"game-sidebar\""))
        "search is rendered alongside the sidebar rather than inside it")
    (is (= 2 (count (re-seq #"hx-get=\"/games/ABC123/search\"" html)))
        "the form handles Enter and the input handles live search")
    (is (re-find #"refreshSearch from:body" html))
    (is (re-find #"/actions/pull-room\?player-id=7" results-html))
    (is (re-find #"name=\"room-definition-id\"[^>]+value=\"3\""
                 results-html))
    (is (re-find #"/actions/pull-card\?player-id=7" card-results-html))
    (is (re-find #"name=\"card-type\"[^>]+value=\"2\"" card-results-html))
    (is (re-find #"name=\"card-definition-id\"[^>]+value=\"0\""
                 card-results-html))
    (is (re-find #"data-jump-location" house-results-html))
    (is (re-find #"data-floor=\"ground\"" house-results-html))
    (is (re-find #"data-grid-x=\"4\"" house-results-html))
    (is (re-find #"data-grid-y=\"3\"" house-results-html))
    (is (not (re-find #"/actions/pull-room" house-results-html)))
    (is (re-find #">Ox Bellows — Alex<" player-results-html))
    (is (re-find #">Player<" player-results-html))
    (is (re-find #"data-jump-location" player-results-html))
    (is (re-find #"aria-label=\"Jump to Ox Bellows — Alex\""
                 player-results-html))))

(deftest can-view-every-players-character-sheet
  (let [other-player (assoc (first (:players state))
                            :id 8
                            :name "Blair")
        two-player-state (update state :players conj other-player)
        html (ui/render-ui "ABC123" two-player-state 7 nil)]
    (is (re-find #">Viewing<" html))
    (is (re-find #">Ox Bellows — Alex<" html))
    (is (re-find #">Ox Bellows — Blair<" html))
    (is (re-find #"data-viewed-player-id=\"7\"" html))
    (is (re-find #"data-viewed-player-id=\"8\" hidden" html))))

(deftest renders-only-requested-update-regions
  (let [traits-html (ui/render-updates
                     "ABC123" state 7 nil #{[:traits 7]})
        inventory-html (ui/render-updates
                        "ABC123" state 7 nil #{[:inventory 7]})]
    (testing "a trait update does not replace the player's inventory"
      (is (re-find #"id=\"player-7-traits\"" traits-html))
      (is (not (re-find #"id=\"player-7-inventory\"" traits-html)))
      (is (not (re-find #"id=\"dice-panel\"" traits-html))))
    (testing "inventory cards have stable identities"
      (is (re-find #"id=\"player-7-inventory\"" inventory-html))
      (is (re-find #"id=\"inventory-card-42\"" inventory-html))
      (is (re-find #"name=\"inventory-card\"" inventory-html))
      (is (re-find #"hx-preserve" inventory-html))
      (is (re-find #"hx-partial" inventory-html))
      (is (re-find #"aria-label=\"Close card details\"" inventory-html))
      (is (re-find #">×</button>" inventory-html))
      (is (not (re-find #"id=\"player-7-traits\"" inventory-html))))))
