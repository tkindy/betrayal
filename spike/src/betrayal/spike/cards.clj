(ns betrayal.spike.cards
  (:require [clojure.string :as str]))

(defn render-roll-table [value]
  [:table.roll-table
   [:tbody
    (for [row (str/split-lines value)
          :let [[_ target outcome] (re-matches #"(\S+)\s{2}(.*)" row)]]
      [:tr [:th target] [:td outcome]])]])

(defn card-copy
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
