install:
    cd ui && npm ci

dev:
    ./bin/dev

build: build-api build-ui

build-api:
    cd api && ./gradlew assemble

build-ui:
    cd ui && npm run build

test: test-api test-ui

test-api:
    cd api && ./gradlew test

test-ui:
    cd ui && CI=true npm test -- --watchAll=false
