(ns betrayal.spike.db
  (:require [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private options {:builder-fn rs/as-unqualified-lower-maps})

(defn datasource []
  (if-let [url (System/getenv "JDBC_DATABASE_URL")]
    (jdbc/get-datasource {:jdbcUrl url})
    (if-let [host (System/getenv "DB_HOST")]
      (jdbc/get-datasource
       {:jdbcUrl (format "jdbc:postgresql://%s:5432/%s"
                         host (or (System/getenv "DB_NAME") "betrayal"))
        :username (System/getenv "DB_USER")
        :password (System/getenv "DB_PASSWORD")})
      (throw
       (ex-info
        "Set JDBC_DATABASE_URL, or set DB_HOST, DB_NAME, DB_USER, and DB_PASSWORD"
        {})))))

(defn games [ds]
  (let [games (jdbc/execute! ds
                             ["select id, name from games order by name"]
                             options)
        players-by-game
        (group-by
         :game_id
         (jdbc/execute!
          ds
          [(str "select id, name, \"gameId\" as game_id"
                " from players order by \"gameId\", id")]
          options))]
    (mapv #(assoc % :players (get players-by-game (:id %) [])) games)))

(defn game [ds game-id]
  (jdbc/execute-one! ds
                     ["select id, name from games where id = ?" game-id]
                     options))

(defn player-in-game? [ds game-id player-id]
  (some?
   (jdbc/execute-one!
    ds
    ["select id from players where \"gameId\" = ? and id = ?"
     game-id player-id]
    options)))

(defn board [connectable game-id]
  {:rooms
   (jdbc/execute!
    connectable
    [(str "select id, \"roomDefId\" as room_def_id,"
          " \"gridX\" as grid_x, \"gridY\" as grid_y, rotation"
          " from rooms where \"gameId\" = ? order by id")
     game-id]
    options)
   :players
   (jdbc/execute!
    connectable
    [(str "select id, name, \"characterId\" as character_id,"
          " \"gridX\" as grid_x, \"gridY\" as grid_y,"
          " \"speedIndex\" as speed_index, \"mightIndex\" as might_index,"
          " \"sanityIndex\" as sanity_index, \"knowledgeIndex\" as knowledge_index"
          " from players where \"gameId\" = ? order by id")
     game-id]
    options)
   :monsters
   (jdbc/execute!
    connectable
    [(str "select id, number, \"gridX\" as grid_x, \"gridY\" as grid_y"
          " from monsters where \"gameId\" = ? order by number")
     game-id]
    options)})

(defn game-state [connectable game-id]
  (merge
   (board connectable game-id)
   {:inventories
    (jdbc/execute!
     connectable
     [(str "select pi.id, pi.\"playerId\" as player_id,"
           " pi.\"cardTypeId\" as card_type_id, pi.\"cardDefId\" as card_def_id"
           " from \"playerInventories\" pi"
           " join players p on p.id = pi.\"playerId\""
           " where p.\"gameId\" = ? order by pi.id")
      game-id]
     options)
    :drawn-card
    (jdbc/execute-one!
     connectable
     [(str "select id, \"cardTypeId\" as card_type_id,"
           " \"cardDefId\" as card_def_id"
           " from \"drawnCards\" where \"gameId\" = ?")
      game-id]
     options)
    :latest-roll
    (jdbc/execute-one!
     connectable
     [(str "select id, rolls, coalesce(type, 'AD_HOC') as type"
           " from \"diceRolls\" where \"gameId\" = ?"
           " order by id desc limit 1")
      game-id]
     options)
    :room-stack
    (jdbc/execute-one!
     connectable
     [(str "select rs.id, rs.\"curIndex\" as cur_index, rs.flipped,"
           " rs.rotation, rsc.id as content_id,"
           " rsc.\"roomDefId\" as room_def_id"
           " from \"roomStacks\" rs"
           " left join \"roomStackContents\" rsc"
           " on rsc.\"stackId\" = rs.id and rsc.index = rs.\"curIndex\""
           " where rs.\"gameId\" = ?")
      game-id]
     options)}))

(defn- entity-in-game? [tx table game-id entity-id]
  (some?
   (jdbc/execute-one!
    tx
    [(format "select id from %s where id = ? and \"gameId\" = ?" table)
     entity-id game-id]
    options)))

(defn- room-at? [tx game-id grid-x grid-y]
  (some?
   (jdbc/execute-one!
    tx
    [(str "select id from rooms where \"gameId\" = ?"
          " and \"gridX\" = ? and \"gridY\" = ?")
     game-id grid-x grid-y]
    options)))

(defn- require-entity! [tx table game-id entity-id]
  (when-not (entity-in-game? tx table game-id entity-id)
    (throw (ex-info (format "%s %s is not in this game" table entity-id) {}))))

(defn- require-room! [tx game-id grid-x grid-y]
  (when-not (room-at? tx game-id grid-x grid-y)
    (throw (ex-info "Players and monsters must be placed in a room" {}))))

(defmulti move!
  (fn [_ _ kind _ _ _] kind))

(defmethod move! :room [ds game-id _ room-id grid-x grid-y]
  (jdbc/with-transaction [tx ds]
    (require-entity! tx "rooms" game-id room-id)
    (let [room (jdbc/execute-one!
                tx
                [(str "select \"gridX\" as grid_x, \"gridY\" as grid_y"
                      " from rooms where id = ? for update")
                 room-id]
                options)]
      (when (and (not= [(:grid_x room) (:grid_y room)] [grid-x grid-y])
                 (room-at? tx game-id grid-x grid-y))
        (throw (ex-info "That space already contains a room" {})))
      (jdbc/execute-one!
       tx
       [(str "update rooms set \"gridX\" = ?, \"gridY\" = ?"
             " where id = ? and \"gameId\" = ?")
        grid-x grid-y room-id game-id])
      (doseq [table ["players" "monsters"]]
        (jdbc/execute-one!
         tx
         [(format
           (str "update %s set \"gridX\" = ?, \"gridY\" = ?"
                " where \"gameId\" = ? and \"gridX\" = ? and \"gridY\" = ?")
           table)
          grid-x grid-y game-id (:grid_x room) (:grid_y room)])))))

(defmethod move! :player [ds game-id _ player-id grid-x grid-y]
  (jdbc/with-transaction [tx ds]
    (require-entity! tx "players" game-id player-id)
    (require-room! tx game-id grid-x grid-y)
    (jdbc/execute-one!
     tx
     [(str "update players set \"gridX\" = ?, \"gridY\" = ?"
           " where id = ? and \"gameId\" = ?")
      grid-x grid-y player-id game-id])))

(defmethod move! :monster [ds game-id _ monster-id grid-x grid-y]
  (jdbc/with-transaction [tx ds]
    (require-entity! tx "monsters" game-id monster-id)
    (require-room! tx game-id grid-x grid-y)
    (jdbc/execute-one!
     tx
     [(str "update monsters set \"gridX\" = ?, \"gridY\" = ?"
           " where id = ? and \"gameId\" = ?")
      grid-x grid-y monster-id game-id])))

(defn rotate-room! [ds game-id room-id]
  (jdbc/with-transaction [tx ds]
    (require-entity! tx "rooms" game-id room-id)
    (jdbc/execute-one!
     tx
     [(str "update rooms set rotation = mod(rotation + 3, 4)"
           " where id = ? and \"gameId\" = ?")
      room-id game-id])))

(def ^:private starting-room-definition-ids #{0 1 2 8 10 33})

(declare require-room-stack!)

(defn return-room! [ds game-id room-id]
  (jdbc/with-transaction [tx ds]
    (let [room (or
                (jdbc/execute-one!
                 tx
                 [(str "select \"roomDefId\" as room_def_id,"
                       " \"gridX\" as grid_x, \"gridY\" as grid_y"
                       " from rooms where id = ? and \"gameId\" = ? for update")
                  room-id game-id]
                 options)
                (throw (ex-info "That room is not part of this game" {})))
          stack (require-room-stack! tx game-id)]
      (when (starting-room-definition-ids (:room_def_id room))
        (throw (ex-info "Starting rooms cannot be returned to the stack" {})))
      (when (:flipped stack)
        (throw (ex-info "Place the flipped room before returning another room"
                        {})))
      (doseq [table ["players" "monsters"]]
        (when (jdbc/execute-one!
               tx
               [(format
                 (str "select id from %s where \"gameId\" = ?"
                      " and \"gridX\" = ? and \"gridY\" = ? limit 1")
                 table)
                game-id (:grid_x room) (:grid_y room)]
               options)
          (throw (ex-info "A room containing players or monsters cannot be returned"
                          {}))))
      (jdbc/execute-one!
       tx ["delete from rooms where id = ? and \"gameId\" = ?" room-id game-id])
      (let [next-index
            (inc (or
                  (:last_index
                   (jdbc/execute-one!
                    tx
                    [(str "select max(index) as last_index"
                          " from \"roomStackContents\" where \"stackId\" = ?")
                     (:id stack)]
                    options))
                  -1))]
        (jdbc/execute-one!
         tx
         [(str "insert into \"roomStackContents\""
               " (\"stackId\", index, \"roomDefId\") values (?, ?, ?)")
          (:id stack) next-index (:room_def_id room)]))
      (let [content-ids
            (map :id
                 (jdbc/execute!
                  tx
                  [(str "select id from \"roomStackContents\""
                        " where \"stackId\" = ? order by random()")
                   (:id stack)]
                  options))]
        (doseq [[index content-id] (map-indexed vector content-ids)]
          (jdbc/execute-one!
           tx
           ["update \"roomStackContents\" set index = ? where id = ?"
            index content-id])))
      (jdbc/execute-one!
       tx
       ["update \"roomStacks\" set \"curIndex\" = 0 where id = ?"
        (:id stack)]))))

(defn roll-dice! [ds game-id num-dice type]
  (when-not (<= 1 num-dice 8)
    (throw (ex-info "Choose between 1 and 8 dice" {})))
  (when-not (#{"AD_HOC" "HAUNT"} type)
    (throw (ex-info "Unknown dice roll type" {})))
  (let [values (repeatedly num-dice #(rand-int 3))]
    (jdbc/execute-one!
     ds
     [(str "insert into \"diceRolls\" (\"gameId\", rolls, type)"
           " values (?, ?, ?)")
      game-id (str/join "|" values) type])
    values))

(def ^:private trait-columns
  {"speed" "speedIndex"
   "might" "mightIndex"
   "sanity" "sanityIndex"
   "knowledge" "knowledgeIndex"})

(defn set-trait! [ds game-id player-id trait index]
  (let [column (get trait-columns trait)]
    (when-not column
      (throw (ex-info "Unknown trait" {})))
    (when-not (<= 0 index 7)
      (throw (ex-info "Trait index must be between 0 and 7" {})))
    (jdbc/with-transaction [tx ds]
      (require-entity! tx "players" game-id player-id)
      (jdbc/execute-one!
       tx
       [(format "update players set \"%s\" = ? where id = ? and \"gameId\" = ?"
                column)
        index player-id game-id]))))

(defn add-monster! [ds game-id]
  (jdbc/with-transaction [tx ds]
    (let [number (inc (or (:number
                           (jdbc/execute-one!
                            tx
                            ["select max(number) as number from monsters where \"gameId\" = ?"
                             game-id]
                            options))
                          0))
          entrance (jdbc/execute-one!
                    tx
                    [(str "select \"gridX\" as grid_x, \"gridY\" as grid_y"
                          " from rooms where \"gameId\" = ? and \"roomDefId\" = 0")
                     game-id]
                    options)]
      (when-not entrance
        (throw (ex-info "This game has no Entrance Hall" {})))
      (jdbc/execute-one!
       tx
       [(str "insert into monsters (\"gameId\", number, \"gridX\", \"gridY\")"
             " values (?, ?, ?, ?)")
        game-id number (:grid_x entrance) (:grid_y entrance)]))))

(defn draw-card! [ds game-id card-type-id]
  (when-not (#{0 1 2} card-type-id)
    (throw (ex-info "Unknown card type" {})))
  (jdbc/with-transaction [tx ds]
    (when (jdbc/execute-one!
           tx
           ["select id from \"drawnCards\" where \"gameId\" = ?" game-id]
           options)
      (throw (ex-info "Resolve the currently drawn card first" {})))
    (let [stack (jdbc/execute-one!
                 tx
                 [(str "select id, \"curIndex\" as cur_index from \"cardStacks\""
                       " where \"gameId\" = ? and \"cardTypeId\" = ? for update")
                  game-id card-type-id]
                 options)
          content (when (:cur_index stack)
                    (jdbc/execute-one!
                     tx
                     [(str "select id, \"cardDefId\" as card_def_id"
                           " from \"cardStackContents\""
                           " where \"stackId\" = ? and index = ?")
                      (:id stack) (:cur_index stack)]
                     options))]
      (when-not content
        (throw (ex-info "That card stack is empty" {})))
      (jdbc/execute-one!
       tx ["delete from \"cardStackContents\" where id = ?" (:id content)])
      (jdbc/execute-one!
       tx
       [(str "insert into \"drawnCards\""
             " (\"gameId\", \"cardTypeId\", \"cardDefId\") values (?, ?, ?)")
        game-id card-type-id (:card_def_id content)])
      (let [next-card (jdbc/execute-one!
                       tx
                       [(str "select min(index) as next_index from \"cardStackContents\""
                             " where \"stackId\" = ?")
                        (:id stack)]
                       options)]
        (jdbc/execute-one!
         tx
         ["update \"cardStacks\" set \"curIndex\" = ? where id = ?"
          (:next_index next-card) (:id stack)])))))

(defn discard-drawn-card! [ds game-id]
  (jdbc/execute-one!
   ds ["delete from \"drawnCards\" where \"gameId\" = ?" game-id]))

(defn give-drawn-card! [ds game-id player-id]
  (jdbc/with-transaction [tx ds]
    (require-entity! tx "players" game-id player-id)
    (let [card (jdbc/execute-one!
                tx
                [(str "select \"cardTypeId\" as card_type_id,"
                      " \"cardDefId\" as card_def_id from \"drawnCards\""
                      " where \"gameId\" = ? for update")
                 game-id]
                options)]
      (when-not card
        (throw (ex-info "There is no drawn card" {})))
      (jdbc/execute-one!
       tx
       [(str "insert into \"playerInventories\""
             " (\"playerId\", \"cardTypeId\", \"cardDefId\") values (?, ?, ?)")
        player-id (:card_type_id card) (:card_def_id card)])
      (discard-drawn-card! tx game-id))))

(defn discard-held-card! [ds game-id player-id card-id]
  (jdbc/with-transaction [tx ds]
    (require-entity! tx "players" game-id player-id)
    (jdbc/execute-one!
     tx
     [(str "delete from \"playerInventories\""
           " where id = ? and \"playerId\" = ?")
      card-id player-id])))

(defn give-held-card! [ds game-id from-player-id card-id to-player-id]
  (jdbc/with-transaction [tx ds]
    (require-entity! tx "players" game-id from-player-id)
    (require-entity! tx "players" game-id to-player-id)
    (let [result (jdbc/execute-one!
                  tx
                  [(str "update \"playerInventories\" set \"playerId\" = ?"
                        " where id = ? and \"playerId\" = ?")
                   to-player-id card-id from-player-id])]
      (when (zero? (:next.jdbc/update-count result))
        (throw (ex-info "That card is not in this inventory" {}))))))

(defn- require-room-stack! [tx game-id]
  (or
   (jdbc/execute-one!
    tx
    [(str "select id, \"curIndex\" as cur_index, flipped, rotation"
          " from \"roomStacks\" where \"gameId\" = ? for update")
     game-id]
    options)
   (throw (ex-info "This game has no room stack" {}))))

(defn- next-stack-index [tx stack-id current-index]
  (or
   (:next_index
    (jdbc/execute-one!
     tx
     [(str "select min(index) as next_index from \"roomStackContents\""
           " where \"stackId\" = ? and index > ?")
      stack-id current-index]
     options))
   (:next_index
    (jdbc/execute-one!
     tx
     [(str "select min(index) as next_index from \"roomStackContents\""
           " where \"stackId\" = ?")
      stack-id]
     options))))

(defn advance-room-stack! [ds game-id]
  (jdbc/with-transaction [tx ds]
    (let [stack (require-room-stack! tx game-id)]
      (when (nil? (:cur_index stack))
        (throw (ex-info "The room stack is empty" {})))
      (when (:flipped stack)
        (throw (ex-info "Place the flipped room before skipping" {})))
      (jdbc/execute-one!
       tx
       ["update \"roomStacks\" set \"curIndex\" = ? where id = ?"
        (next-stack-index tx (:id stack) (:cur_index stack))
        (:id stack)]))))

(defn flip-room-stack! [ds game-id]
  (jdbc/with-transaction [tx ds]
    (let [stack (require-room-stack! tx game-id)]
      (when (nil? (:cur_index stack))
        (throw (ex-info "The room stack is empty" {})))
      (when (:flipped stack)
        (throw (ex-info "The current room is already flipped" {})))
      (jdbc/execute-one!
       tx
       ["update \"roomStacks\" set flipped = true, rotation = 0 where id = ?"
        (:id stack)]))))

(defn rotate-room-stack! [ds game-id]
  (jdbc/with-transaction [tx ds]
    (let [stack (require-room-stack! tx game-id)]
      (when-not (:flipped stack)
        (throw (ex-info "Flip a room before rotating it" {})))
      (jdbc/execute-one!
       tx
       ["update \"roomStacks\" set rotation = ? where id = ?"
        (mod (+ (:rotation stack) 3) 4) (:id stack)]))))

(defn place-room! [ds game-id grid-x grid-y]
  (jdbc/with-transaction [tx ds]
    (let [stack (require-room-stack! tx game-id)]
      (when-not (:flipped stack)
        (throw (ex-info "Flip a room before placing it" {})))
      (when (room-at? tx game-id grid-x grid-y)
        (throw (ex-info "That space already contains a room" {})))
      (let [content (jdbc/execute-one!
                     tx
                     [(str "select id, \"roomDefId\" as room_def_id"
                           " from \"roomStackContents\""
                           " where \"stackId\" = ? and index = ?")
                      (:id stack) (:cur_index stack)]
                     options)]
        (when-not content
          (throw (ex-info "The room stack is empty" {})))
        (jdbc/execute-one!
         tx
         [(str "insert into rooms"
               " (\"gameId\", \"roomDefId\", \"gridX\", \"gridY\", rotation)"
               " values (?, ?, ?, ?, ?)")
          game-id (:room_def_id content) grid-x grid-y (:rotation stack)])
        (jdbc/execute-one!
         tx ["delete from \"roomStackContents\" where id = ?" (:id content)])
        (jdbc/execute-one!
         tx
         [(str "update \"roomStacks\" set \"curIndex\" = ?,"
               " flipped = false, rotation = null where id = ?")
          (next-stack-index tx (:id stack) (:cur_index stack))
          (:id stack)])))))
