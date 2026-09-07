# Clojure application

This prototype tests replacing the React/Konva UI with a server-rendered game
screen. The board is rendered as SVG, while pan, zoom, dragging, fragment swaps,
and other DOM interactions live in a small browser-side module.

It reads and updates the existing Betrayal PostgreSQL schema. It can create a
lobby, initialize a complete game, or resume a game created by the Kotlin
application.

## Run

From `spike/`, set the same JDBC URL used by the API:

```sh
export JDBC_DATABASE_URL='jdbc:postgresql://localhost:5432/postgres?user=postgres&password=mysecretpassword'
clojure -M:run
```

Then open <http://localhost:8081>. Create a lobby and share its six-letter code,
or choose an existing game and player to open its board. The lobby host can
start a game with one to six players. Starting creates the players, starting
rooms, shuffled room stack, and shuffled card stacks in one database
transaction.

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

In production, creating or joining a lobby gives the browser an unguessable
membership token in a signed and encrypted, HTTP-only session cookie. When the
host starts the game, each browser exchanges that membership for its permanent
player binding. The cookie is used for page loads, command requests, and event
streams, survives server restarts after that exchange, and cannot be changed
into another player binding by the browser. The server always verifies that the
selected player belongs to the game.

Waiting lobbies are intentionally process-local, as they were in the original
implementation. A deploy or restart abandons a lobby that has not started yet;
started games and player sessions are durable.

When the app is not running in production, each loopback lobby participant and
game player is instead identified by an unguessable query token scoped to that
tab's URL. The host's displayed share link omits their token, so opening it in
another ordinary tab shows the join form for a new player. Lobby start,
navigation into the game, command URLs, and event streams preserve the
appropriate tab identity. These overrides are disabled in production even when
a reverse proxy makes the incoming connection appear local.

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

The browser submits drops and game controls as ordinary HTMX `POST` requests.
The HTTP response is the command acknowledgement, and `hx-disable` disables only
that form's submitting control while the request is active. Commands are never
queued or replayed after a connection failure.

Each game also has a push-only Server-Sent Events stream. The server persists a
command, acknowledges it with an empty successful HTTP response, and broadcasts
the affected server-rendered HTML regions to the connected clients. HTMX 4's
SSE extension reconnects the stream and receives a complete authoritative state
snapshot on every connection. Unrelated DOM—and therefore client-owned state
such as an open inventory card, focus, the viewed player, and board pan and
zoom—stays in place.

HTMX 4.0.0 and its matching SSE extension are vendored under
`resources/public/vendor/` from the official release archive. They are served
from the application domain and require no npm install, CDN, or separate UI
build. `board.js` is reserved for local SVG gestures and transient UI state.

Game controls are rendered as overlays so the board viewport remains stable
when server fragments update.

## Test

```sh
clojure -M:test
```

## Parallel deployment

`Dockerfile.clojure` and `config/deploy.clojure.yml` define a separate
`betrayal-clojure` service at `betrayal-beta.tylerkindy.com`, so it can run
alongside the original `betrayal` service against the same PostgreSQL database.
Production requires a stable `SESSION_SECRET`; the Kamal secrets file reads it
from the `Betrayal config` 1Password item. Changing this value invalidates all
existing Clojure app sessions.
Deploy it with:

```sh
kamal deploy -c config/deploy.clojure.yml
```

Use only one implementation as the writer during a play session. Both use the
same schema and definition files, so games can be created, opened, and modified
by either implementation. Real-time updates are not relayed between clients
connected to different implementations.

## Future improvements

- Replace the manually constructed SQL strings in the Clojure data layer with a
  composable Clojure SQL library. The current queries remain parameterized, but
  a library would make dynamic statements easier to read and maintain.
