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

The games listing shows a link for each player in a game. On loopback requests,
that link's `player-id` query parameter identifies who that tab is playing as
and is carried into its WebSocket connection. This makes it possible to play as
different people in ordinary tabs. The server ignores this development
override on non-loopback requests and always verifies that the player belongs
to the game.

The **Viewing** control in the bottom bar is independent of that identity. It
can inspect any player's traits and inventory without changing who the tab is
playing as.

## Interactions

- Drag empty board space to pan.
- Scroll or use a trackpad to zoom around the pointer.
- Drag a room to an unoccupied grid cell.
- Drag a player or monster to another room.
- Roll ordinary or haunt dice.
- Draw, inspect, give, and discard cards.
- Take a drawn card directly for the tab's acting player.
- Select a character and update their traits and inventory.
- Add and move monsters.
- Flip, rotate, skip, and place rooms from the room stack.

The browser sends drops and game-control commands over one WebSocket connection.
The server validates and persists each command, then broadcasts only the keyed
board and UI regions affected by that command. Unrelated DOM—and therefore
client-owned state such as an open inventory card, focus, or the viewed
player—stays in place. When an inventory does change, stable card IDs preserve
the open state of cards that remain in that inventory. Invalid commands return
authoritative fragments with an error only to the initiating client.

Game controls are rendered as overlays so the board viewport remains stable
when server fragments update.

## Test

```sh
clojure -M:test
```
