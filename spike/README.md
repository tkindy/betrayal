# Clojure board spike

This prototype tests the riskiest part of replacing the React/Konva UI:
server-rendering an existing game's board as SVG while keeping pan, zoom, and
drag interactions in a small browser-side module.

It reads and updates the existing Betrayal PostgreSQL schema. It does not run
migrations or create games, so point it at a database already used by the
Kotlin application.

## Run

From `spike/`, set the same JDBC URL used by the API:

```sh
export JDBC_DATABASE_URL='jdbc:postgresql://localhost:5432/postgres?user=postgres&password=mysecretpassword'
clojure -M:run
```

Then open <http://localhost:8081>. The index lists every game in that database;
choose one to open its board.

Set `PORT` to use a port other than 8081. Run from `spike/` so the prototype can
read the API's existing `rooms.csv` and `characters.csv` definitions without
duplicating them. Alternatively, set `BETRAYAL_DEFINITIONS_DIR` to the directory
containing those files.

## Interactions

- Drag empty board space to pan.
- Scroll or use a trackpad to zoom around the pointer.
- Drag a room to an unoccupied grid cell.
- Drag a player or monster to another room.

Drops are posted to the Clojure server. The server validates and persists the
move, renders a new `#board-state` fragment, and the browser swaps that fragment
without resetting its local viewport. Invalid drops return an error in the
same authoritative fragment.

This intentionally does not implement the sidebar, room drawing/placement, or
real-time multiplayer updates. Its purpose is to decide whether server-rendered
SVG plus focused JavaScript is a good foundation before migrating the rest of
the application.

## Test

```sh
clojure -M:test
```
