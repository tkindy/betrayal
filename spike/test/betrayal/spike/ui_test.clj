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
      (is (not (re-find #"<h2>(Dice|Cards|Room stack)</h2>" html)))
      (is (< (.indexOf html "id=\"zoom-panel\"")
             (.indexOf html "id=\"game-sidebar\""))
          "zoom controls render separately from, and before, the sidebar"))
    (testing "the latest roll and held card are rendered"
      (is (re-find #"Total: 3" html))
      (is (re-find #"Chainsaw" html)))))

(deftest reuses-board-room-rendering-in-picker
  (let [flipped (assoc-in state [:room-stack :flipped] true)
        html (ui/render-ui "ABC123" flipped 7 nil)]
    (is (re-find #"room-picker-preview flipped" html))
    (is (re-find #"class=\"room\"" html))
    (is (re-find #">Nursery<" html))
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

(deftest can-view-every-players-character-sheet
  (let [other-player (assoc (first (:players state))
                            :id 8
                            :name "Blair")
        two-player-state (update state :players conj other-player)
        html (ui/render-ui "ABC123" two-player-state 7 nil)]
    (is (re-find #">Viewing<" html))
    (is (re-find #">Alex — Ox Bellows<" html))
    (is (re-find #">Blair — Ox Bellows<" html))
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
