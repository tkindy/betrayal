install:
    cd ui && npm ci

dev:
    ./bin/dev

build: build-api build-ui build-clojure

build-api:
    cd api && ./gradlew assemble

build-ui:
    cd ui && npm run build

build-clojure:
    cd spike && clojure -T:build uber

test: test-api test-ui test-clojure

test-api:
    cd api && ./gradlew test

test-ui:
    cd ui && CI=true npm test -- --watchAll=false

test-clojure:
    cd spike && clojure -M:test
