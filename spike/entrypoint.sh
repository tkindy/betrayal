#!/usr/bin/env bash

set -euo pipefail

database_url="${JDBC_DATABASE_URL:-jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}}"

migrated=false
for attempt in {1..30}; do
  liquibase_args=(
    --search-path=/app
    --changelog-file=migrations.sql
    --url="$database_url"
  )
  if [[ -z "${JDBC_DATABASE_URL:-}" ]]; then
    liquibase_args+=(--username="$DB_USER" --password="$DB_PASSWORD")
  fi

  if /opt/liquibase/liquibase "${liquibase_args[@]}" update; then
    migrated=true
    break
  fi

  echo "Database migration attempt $attempt failed; retrying in 2 seconds" >&2
  sleep 2
done

if [[ "$migrated" != true ]]; then
  echo "Database migrations failed after 30 attempts" >&2
  exit 1
fi

exec java -jar /app/betrayal-clojure.jar
