(ns betrayal.spike.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private options {:builder-fn rs/as-unqualified-lower-maps})

(defn datasource []
  (if-let [url (System/getenv "JDBC_DATABASE_URL")]
    (jdbc/get-datasource {:jdbcUrl url})
    (throw
     (ex-info
      "JDBC_DATABASE_URL is required (for example jdbc:postgresql://localhost:5432/postgres?user=postgres&password=...)"
      {}))))

(defn games [ds]
  (jdbc/execute! ds
                 ["select id, name from games order by name"]
                 options))

(defn game [ds game-id]
  (jdbc/execute-one! ds
                     ["select id, name from games where id = ?" game-id]
                     options))

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
          " \"gridX\" as grid_x, \"gridY\" as grid_y"
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
