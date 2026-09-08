(ns betrayal.spike.board-test
  (:require [betrayal.spike.board :as board]
            [clojure.test :refer [deftest is testing]]
            [hiccup2.core :as h]))

(deftest rotates-doors
  (is (= [\N \E \S \W] (board/rotate-doors "NESW" 0)))
  (is (= [\W \N \E \S] (board/rotate-doors "NESW" 1)))
  (is (= [\E] (board/rotate-doors "N" 3)))
  (is (= [\W] (board/rotate-doors "N" 5))))

(deftest finds-valid-open-spots
  (let [state {:rooms [{:grid_x 0 :grid_y 0 :doors "N"}]
               :room-stack {:flipped true :doors "S"}}]
    (is (= [[0 -1]] (board/open-spots state)))
    (is (empty? (board/open-spots
                 (assoc-in state [:room-stack :doors] "N"))))
    (is (empty? (board/open-spots
                 (assoc-in state [:room-stack :flipped] false))))))

(deftest calculates-board-bounds
  (is (= {:min-x -8 :max-x 10 :min-y -8 :max-y 12}
         (board/bounds
          {:rooms [{:grid_x 4 :grid_y 3}
                   {:grid_x -8 :grid_y -8}
                   {:grid_x 10 :grid_y 12}]})))
  (is (= {:min-x 0 :max-x 0 :min-y 0 :max-y 0}
         (board/bounds {:rooms []}))))

(deftest derives-floors-from-landing-connected-components
  (let [rooms [{:id 1 :room_def_id 0 :grid_x 4 :grid_y 3}
               {:id 2 :room_def_id 1 :grid_x 3 :grid_y 3}
               {:id 3 :room_def_id 8 :grid_x 104 :grid_y 3}
               {:id 4 :room_def_id 20 :grid_x 104 :grid_y 4}
               {:id 5 :room_def_id 10 :grid_x 204 :grid_y 3}
               {:id 6 :room_def_id 33 :grid_x -96 :grid_y 3}
               {:id 7 :room_def_id 30 :grid_x 106 :grid_y 3}]
        layout (into {} (map (juxt :key identity)
                             (board/floor-layout {:rooms rooms})))]
    (is (= [1 2] (mapv :id (get-in layout ["ground" :rooms]))))
    (is (= [3 4 7] (mapv :id (get-in layout ["upper" :rooms])))
        "a detached movable room remains on its nearest floor")
    (is (= {:min-x 104 :max-x 106 :min-y 3 :max-y 4}
           (get-in layout ["upper" :bounds])))))

(deftest room-roll-tables-have-an-explicit-placeholder
  (let [description (:description (get @board/room-definitions 36))]
    (is (re-find #"\n<rollTable>\n" description))
    (is (re-find #"4  Any floor" description))))

(deftest renders-an-svg-fragment
  (let [html (board/render-board
              {:rooms [{:id 1 :room_def_id 0 :grid_x 4 :grid_y 3 :rotation 0}]
               :players [{:id 2 :name "Player <one>" :character_id 0
                          :grid_x 4 :grid_y 3
                          :speed_index 2 :might_index 2
                          :sanity_index 2 :knowledge_index 2}]
               :monsters [{:id 3 :number 1 :grid_x 4 :grid_y 3}]})]
    (testing "the fragment contains all draggable entity types"
      (is (re-find #"data-kind=\"room\"" html))
      (is (re-find #"data-id=\"1\" data-kind=\"room\"" html))
      (is (re-find #"data-kind=\"player\"" html))
      (is (re-find #"data-kind=\"monster\"" html)))
    (testing "floor controls and canvases are rendered with local bounds"
      (is (= 4 (count (re-seq #"class=\"floor-select\"" html))))
      (is (= 4 (count (re-seq #"class=\"floor-canvas\"" html))))
      (is (re-find #"id=\"floor-drawer-toggle\"" html))
      (is (re-find #"aria-controls=\"floor-navigation\"" html))
      (is (re-find #"aria-expanded=\"false\"" html))
      (is (re-find #"data-floor-select=\"ground\"" html))
      (is (re-find #"data-max-x=\"4\"" html))
      (is (re-find #"data-min-x=\"4\"" html))
      (is (< (.indexOf html "data-floor-select=\"roof\"")
             (.indexOf html "data-floor-select=\"upper\"")
             (.indexOf html "data-floor-select=\"ground\"")
             (.indexOf html "data-floor-select=\"basement\""))
          "floor miniatures render top-to-bottom so basement is at the bottom")
      (is (= 1 (count (re-seq #"class=\"minimap-token minimap-player\"" html))))
      (is (= 1 (count (re-seq #"class=\"minimap-token minimap-monster\"" html))))
      (is (re-find #"minimap-player\" transform=\"translate\(4\.375 3\.5\)\""
                   html))
      (is (re-find #"minimap-monster\" transform=\"translate\(4\.625 3\.5\)\""
                   html))
      (is (not (re-find #"minimap-monster[^>]*>[^<]*<text" html))
          "miniature monster icons omit their numbers"))
    (testing "tokens render in a separate layer above every room"
      (let [rooms-layer (.indexOf html "class=\"rooms\"")
            tokens-layer (.indexOf html "class=\"tokens\"")]
        (is (<= 0 rooms-layer))
        (is (< rooms-layer tokens-layer))))
    (testing "players and monsters share one centered row"
      (is (re-find #"player draggable\"[^>]+translate\(74 137\)" html))
      (is (re-find #"monster draggable\"[^>]+translate\(106 137\)" html)))
    (testing "room details use the app hovercard rather than an SVG title"
      (is (re-find #"data-description=\"\"" html))
      (is (re-find #"id=\"room-details\"" html))
      (is (re-find #"class=\"room-details-description\"" html))
      (is (re-find #"aria-label=\"Room actions\"" html))
      (is (re-find #">⋯</summary>" html))
      (is (re-find #"hx-post=\"/games//actions/rotate-room\"" html))
      (is (re-find #"hx-post=\"/games//actions/return-room\"" html))
      (is (not (re-find #"<title>Entrance Hall</title>" html))))
    (testing "player tokens provide data for an app hovercard"
      (is (re-find #"data-player-name=\"Player &lt;one&gt;\"" html))
      (is (re-find #"data-character-name=\"Ox Bellows\"" html))
      (is (re-find #"aria-label=\"Ox Bellows — Player &lt;one&gt;\"" html))
      (is (re-find #"data-speed=\"2\"" html))
      (is (re-find #"id=\"player-details\"" html))
      (is (< (.indexOf html "player-details-character")
             (.indexOf html "player-details-name"))
          "the prominent character name precedes the player name")
      (is (not (re-find #"<title>Player" html))))
    (testing "Hiccup escapes database content"
      (is (re-find #"Player &lt;one&gt;" html)))))

(deftest indicates-placed-rooms-with-additional-rules
  (let [html (board/render-board
              {:rooms [{:id 1 :room_def_id 3
                        :grid_x 0 :grid_y 0 :rotation 0}
                       {:id 2 :room_def_id 0
                        :grid_x 0 :grid_y 1 :rotation 0}]
               :players []
               :monsters []})]
    (is (= 1 (count (re-seq #"class=\"room-rules-icon\"" html))))
    (is (re-find #"aria-label=\"Nursery — additional rules\"" html))
    (is (re-find #"aria-label=\"Entrance Hall\"" html))
    (is (not (re-find #"aria-label=\"Entrance Hall — additional rules\"" html)))))

(deftest renders-a-reusable-room-tile
  (let [html (str (h/html
                   (board/room-tile
                    {:name "Gallery" :doors "NS"
                     :features "O" :barrier-features "I"})))]
    (is (re-find #"class=\"room\"" html))
    (is (re-find #">Gallery<" html))
    (is (re-find #">O I<" html))
    (is (not (re-find #"class=\"room-rules-icon\"" html)))
    (is (= 2 (count (re-seq #"class=\"door\"" html))))))
