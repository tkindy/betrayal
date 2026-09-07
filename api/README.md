# betrayal-api

The API for my Betrayal at House on the Hill companion app 

## Running locally

Start a Postgres instance.

```shell
docker run --name betrayal-db -e POSTGRES_PASSWORD=mysecretpassword -d -p 5432:5432 postgres:12.6
```

Run the migrations using Liquibase.

```shell
JDBC_DATABASE_URL="jdbc:postgresql:postgres?user=postgres&password=mysecretpassword" ./gradlew update
```

Then, start the API, providing the `JDBC_DATABASE_URL` environment variable.
