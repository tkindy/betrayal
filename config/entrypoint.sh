#!/usr/bin/env bash

set -euo pipefail

database_url="jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}"

migrated=false
for attempt in {1..30}; do
  if /opt/liquibase/liquibase \
    --search-path=/app \
    --changelog-file=migrations.sql \
    --url="$database_url" \
    --username="$DB_USER" \
    --password="$DB_PASSWORD" \
    update; then
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

/app/api/bin/betrayal-api &
api_pid=$!

nginx -g "daemon off;" &
nginx_pid=$!

shutdown() {
  kill -TERM "$api_pid" "$nginx_pid" 2>/dev/null || true
  wait "$api_pid" "$nginx_pid" 2>/dev/null || true
}

trap shutdown TERM INT EXIT

while kill -0 "$api_pid" 2>/dev/null && kill -0 "$nginx_pid" 2>/dev/null; do
  sleep 1
done

exit 1
