(ns betrayal.spike.board-test
  (:require [betrayal.spike.board :as board]
            [clojure.test :refer [deftest is testing]]))

(deftest rotates-doors-clockwise
  (is (= [\N \E \S \W] (board/rotate-doors "NESW" 0)))
  (is (= [\E \S \W \N] (board/rotate-doors "NESW" 1)))
  (is (= [\W] (board/rotate-doors "N" 3)))
  (is (= [\E] (board/rotate-doors "N" 5))))

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
                          :grid_x 4 :grid_y 3}]
               :monsters [{:id 3 :number 1 :grid_x 4 :grid_y 3}]})]
    (testing "the fragment contains all draggable entity types"
      (is (re-find #"data-kind=\"room\"" html))
      (is (re-find #"data-id=\"1\" data-kind=\"room\"" html))
      (is (re-find #"data-kind=\"player\"" html))
      (is (re-find #"data-kind=\"monster\"" html)))
    (testing "Hiccup escapes database content"
      (is (re-find #"Player &lt;one&gt;" html)))))
