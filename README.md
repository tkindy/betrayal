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

Build both applications from the repository root:

```shell
just build
```

The output is written to `api/build/` and `ui/build/`.

Each application can also be built independently:

```shell
just build-api
just build-ui
```

## Deploy

The production image contains both applications. Nginx serves the React UI and
proxies `/api` to the Ktor API in the same container. Liquibase migrations run
automatically before the processes start.

Deployments use [Kamal](https://kamal-deploy.org/) and the PostgreSQL accessory
defined in `config/deploy.yml`.

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
