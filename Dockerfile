FROM node:20-bookworm-slim AS ui-builder

WORKDIR /build/ui

COPY ui/package.json ui/package-lock.json ./
RUN npm ci

COPY ui/ ./
RUN npm run build


FROM eclipse-temurin:21-jdk-noble AS api-builder

WORKDIR /build/api

COPY api/gradlew api/gradlew.bat api/settings.gradle.kts api/build.gradle.kts \
  api/gradle.properties api/buildscript-gradle.lockfile api/gradle.lockfile ./
COPY api/gradle/ gradle/
COPY resources/ /build/resources/
RUN ./gradlew --no-daemon dependencies >/dev/null

COPY api/src/ src/
RUN ./gradlew --no-daemon installDist


FROM liquibase/liquibase:4.31 AS liquibase


FROM eclipse-temurin:21-jre-noble

RUN apt-get update && \
  apt-get install -y --no-install-recommends nginx && \
  rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=ui-builder /build/ui/build/ /usr/share/nginx/html/
COPY --from=api-builder /build/api/build/install/betrayal-api/ api/
COPY --from=liquibase /liquibase/ /opt/liquibase/
COPY resources/migrations.sql ./
COPY config/nginx.conf /etc/nginx/nginx.conf
COPY config/entrypoint.sh ./

RUN chmod +x entrypoint.sh

EXPOSE 80

ENTRYPOINT ["/app/entrypoint.sh"]
