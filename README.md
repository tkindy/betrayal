# Betrayal

My Betrayal at House on the Hill companion app.

## Prerequisites

- JDK 21
- Node.js and npm
- [just](https://github.com/casey/just)
- Docker, for local development and API tests

If more than one JDK is installed, make sure `JAVA_HOME` and `java -version`
both select JDK 21 before running Gradle.

## Setup

Install the UI dependencies:

```shell
just install
```

Gradle downloads the API dependencies on its first invocation.

## Run locally

Start Docker, then run the complete development environment from the repository
root:

```shell
just dev
```

The launcher can also be run directly with `./bin/dev`.

This command selects an installed JDK 21, starts or reuses the local PostgreSQL
container, installs the UI dependencies, runs the database migrations, and
starts both applications. Open http://localhost:3000. The API listens on
http://localhost:8080.

Press Ctrl-C to stop the API and UI. The PostgreSQL container remains running
so its data is preserved for the next session.

## Build

Build all applications from the repository root:

```shell
just build
```

The output is written to `api/build/`, `ui/build/`, and `spike/target/`.

Each application can also be built independently:

```shell
just build-api
just build-ui
just build-clojure
```

Room, character, card, and database schema definitions live in `resources/`.
Both the Kotlin application and the Clojure spike package these same canonical
files, so either implementation can open games created by the other without
maintaining duplicate definitions.

## Deploy

The production image contains both applications. Nginx serves the React UI and
proxies `/api` to the Ktor API in the same container. Liquibase migrations run
automatically before the processes start.

Deployments use [Kamal](https://kamal-deploy.org/) and the PostgreSQL accessory
defined in `config/deploy.yml`.

The Clojure implementation has its own `Dockerfile.clojure` and
`config/deploy.clojure.yml`, with a distinct service and hostname so it can be
deployed alongside the original application against that PostgreSQL accessory.
See `spike/README.md` for the parallel deployment command and writer
coordination rules.

Secrets are pulled from 1Password via `.kamal/secrets`. Deploy with:

```shell
kamal deploy
```

The database is persisted in the `betrayal-db` accessory's `data` directory on
the server. Back it up before server maintenance or PostgreSQL upgrades.

## Test

Start Docker, then run:

```shell
just test
```

See the README in each application directory for application-specific
development and runtime instructions.
