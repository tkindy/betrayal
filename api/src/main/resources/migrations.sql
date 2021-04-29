--liquibase formatted sql

--changeset tkindy:1
CREATE TABLE "games" (
    "id" VARCHAR(6) PRIMARY KEY,
    "name" VARCHAR(32) NOT NULL
);

--changeset tkindy:2
CREATE TABLE "players" (
    "id" SERIAL PRIMARY KEY,
    "gameId" VARCHAR(6) NOT NULL,
    "characterId" SMALLINT NOT NULL,
    "gridX" INT NOT NULL,
    "gridY" INT NOT NULL,
    "speedIndex" SMALLINT NOT NULL,
    "mightIndex" SMALLINT NOT NULL,
    "sanityIndex" SMALLINT NOT NULL,
    "knowledgeIndex" SMALLINT NOT NULL
);

CREATE INDEX ON "players" ("gameId");

--changeset tkindy:3
CREATE TABLE "rooms" (
    "id" SERIAL PRIMARY KEY,
    "gameId" VARCHAR(6) NOT NULL,
    "roomDefId" SMALLINT NOT NULL,
    "gridX" INT NOT NULL,
    "gridY" INT NOT NULL,
    "rotation" SMALLINT NOT NULL
);

CREATE INDEX ON "rooms" ("gameId");

--changeset tkindy:4
CREATE TABLE "roomStacks" (
    "id" SERIAL PRIMARY KEY,
    "gameId" VARCHAR(6) NOT NULL,
    "curIndex" SMALLINT,
    "flipped" BOOLEAN NOT NULL,
    "rotation" SMALLINT
);

CREATE UNIQUE INDEX ON "roomStacks" ("gameId");

CREATE TABLE "roomStackContents" (
    "id" SERIAL PRIMARY KEY,
    "stackId" INT NOT NULL,
    "index" SMALLINT NOT NULL,
    "roomDefId" SMALLINT NOT NULL
);

--changeset tkindy:5
CREATE TABLE "cardStacks" (
    "id" SERIAL PRIMARY KEY,
    "gameId" VARCHAR(6) NOT NULL,
    "cardTypeId" SMALLINT NOT NULL,
    "curIndex" SMALLINT
);

CREATE UNIQUE INDEX ON "cardStacks" ("gameId", "cardTypeId");

CREATE TABLE "cardStackContents" (
    "id" SERIAL PRIMARY KEY,
    "stackId" INT NOT NULL,
    "index" SMALLINT NOT NULL,
    "cardDefId" SMALLINT NOT NULL
);

CREATE UNIQUE INDEX ON "cardStackContents" ("stackId", "index")

--changeset tkindy:6
CREATE TABLE "drawnCards" (
    "id" SERIAL PRIMARY KEY,
    "gameId" VARCHAR(6) NOT NULL,
    "cardTypeId" SMALLINT NOT NULL,
    "cardDefId" SMALLINT NOT NULL
);

CREATE UNIQUE INDEX ON "drawnCards" ("gameId");

--changeset tkindy:7
CREATE TABLE "playerInventories" (
    "id" SERIAL PRIMARY KEY,
    "playerId" INT NOT NULL,
    "cardTypeId" SMALLINT NOT NULL,
    "cardDefId" SMALLINT NOT NULL
);

CREATE INDEX ON "playerInventories" ("playerId");

--changeset tkindy:8
CREATE TABLE "diceRolls" (
    "id" SERIAL PRIMARY KEY,
    "gameId" VARCHAR(6) NOT NULL,
    "rolls" VARCHAR(15) NOT NULL
);

--changeset tkindy:9
CREATE TABLE "monsters" (
    "id" SERIAL PRIMARY KEY,
    "gameId" VARCHAR(6) NOT NULL,
    "number" INT NOT NULL,
    "gridX" INT NOT NULL,
    "gridY" INT NOT NULL
);

CREATE INDEX ON "monsters" ("gameId");

--changeset tkindy:10
ALTER TABLE "rooms" ADD COLUMN "floor" VARCHAR(1);
