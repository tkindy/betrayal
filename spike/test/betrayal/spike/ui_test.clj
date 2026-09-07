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
      (is (re-find #"/actions/roll" html))
      (is (re-find #"/actions/draw-card" html))
      (is (re-find #"/actions/add-monster" html))
      (is (re-find #"/actions/flip-room-stack" html))
      (is (re-find #"/actions/set-trait" html))
      (is (re-find #"/actions/discard-held-card" html)))
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
