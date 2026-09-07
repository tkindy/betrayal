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

Set `PORT` to use a port other than 8081. The shared room, character, card, and
schema definitions live in the repository's `resources/` directory. Development
reads them there, and the standalone build packages them on the application
classpath. Alternatively, set `BETRAYAL_DEFINITIONS_DIR` to a directory
containing the CSV definition files.

Build a standalone jar from `spike/` with:

```sh
clojure -T:build uber
```

The production container also accepts `DB_HOST`, `DB_NAME`, `DB_USER`, and
`DB_PASSWORD` separately and runs the shared Liquibase migrations before
starting.

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
- Scroll, use a trackpad, or use the board-view controls to zoom and fit.
- Drag a room to an unoccupied grid cell.
- Hover over a room to read its rules or safely rotate or return it from its
  contextual menu. Returning a room requires confirmation.
- Drag a player or monster to another room.
- Hover over a player token to see their character and current traits.
- Roll ordinary or haunt dice.
- Draw, inspect, give, and discard cards. One inventory card can be open at a
  time; changing the viewed player closes it.
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

## Parallel deployment

`Dockerfile.clojure` and `config/deploy.clojure.yml` define a separate
`betrayal-clojure` service, so it can run alongside the original `betrayal`
service against the same PostgreSQL database. Set `BETRAYAL_CLOJURE_HOST` to its
separate hostname and deploy it with:

```sh
kamal deploy -c config/deploy.clojure.yml
```

Use only one implementation as the writer during a play session. Both use the
same schema and definition files, so existing games and changes made by either
implementation remain readable by the other. The current spike still relies on
the original application to create a game until the lobby flow is ported.
Real-time updates are not relayed between clients connected to different
implementations.
