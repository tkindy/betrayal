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
      (is (re-find #"aria-label=\"Room actions\"" html))
      (is (re-find #">⋯</summary>" html))
      (is (re-find #"data-action=\"rotate-room\"" html))
      (is (re-find #"data-action=\"return-room\"" html))
      (is (not (re-find #"<title>Entrance Hall</title>" html))))
    (testing "player tokens provide data for an app hovercard"
      (is (re-find #"data-player-name=\"Player &lt;one&gt;\"" html))
      (is (re-find #"data-character-name=\"Ox Bellows\"" html))
      (is (re-find #"data-speed=\"2\"" html))
      (is (re-find #"id=\"player-details\"" html))
      (is (not (re-find #"<title>Player" html))))
    (testing "Hiccup escapes database content"
      (is (re-find #"Player &lt;one&gt;" html)))))

(deftest renders-a-reusable-room-tile
  (let [html (str (h/html
                   (board/room-tile
                    {:name "Gallery" :doors "NS"
                     :features "O" :barrier-features "I"})))]
    (is (re-find #"class=\"room\"" html))
    (is (re-find #">Gallery<" html))
    (is (re-find #">O I<" html))
    (is (= 2 (count (re-seq #"class=\"door\"" html))))))
