# Clojure board spike

This prototype tests replacing the React/Konva UI with a server-rendered game
screen. The board is rendered as SVG, while pan, zoom, dragging, fragment swaps,
and other DOM interactions live in a small browser-side module.

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
- Roll ordinary or haunt dice.
- Draw, inspect, give, and discard cards.
- Select a character and update their traits and inventory.
- Add and move monsters.
- Flip, rotate, skip, and place rooms from the room stack.

Drops are posted to the Clojure server. The server validates and persists the
move, renders a new `#board-state` fragment, and the browser swaps that fragment
without resetting its local viewport. Invalid drops return an error in the
same authoritative fragment.

Game controls are rendered as overlays so the board viewport remains stable
when server fragments update. Real-time multiplayer broadcasts are intentionally
left out; the prototype focuses on the rendering and interaction boundary before
adding the WebSocket transport.

## Test

```sh
clojure -M:test
```
