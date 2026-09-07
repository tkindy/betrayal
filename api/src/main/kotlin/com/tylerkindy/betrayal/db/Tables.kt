package com.tylerkindy.betrayal.db

import org.jetbrains.exposed.sql.Table

object Games : Table() {
    val id = varchar("id", 6)
    val name = varchar("name", 32)

    override val primaryKey = PrimaryKey(id)
}

object Players : Table() {
    val id = integer("id")
    val gameId = varchar("gameId", 6)
    val name = varchar("name", 20)
    val characterId = short("characterId")
    val gridX = integer("gridX")
    val gridY = integer("gridY")
    val speedIndex = short("speedIndex")
    val mightIndex = short("mightIndex")
    val sanityIndex = short("sanityIndex")
    val knowledgeIndex = short("knowledgeIndex")

    override val primaryKey = PrimaryKey(id)
}

object Rooms : Table() {
    val id = integer("id").autoIncrement()
    val gameId = varchar("gameId", 6)
    val roomDefId = short("roomDefId")
    val gridX = integer("gridX")
    val gridY = integer("gridY")
    val rotation = short("rotation")

    override val primaryKey = PrimaryKey(id)
}

object RoomStacks : Table("\"roomStacks\"") {
    val id = integer("id").autoIncrement()
    val gameId = varchar("gameId", 6)
    val curIndex = short("curIndex").nullable()
    val flipped = bool("flipped")
    val rotation = short("rotation").nullable()

    override val primaryKey = PrimaryKey(id)
}

object RoomStackContents : Table("\"roomStackContents\"") {
    val id = integer("id").autoIncrement()
    val stackId = integer("stackId")
    val index = short("index")
    val roomDefId = short("roomDefId")

    override val primaryKey = PrimaryKey(id)
}

object CardStacks : Table("\"cardStacks\"") {
    val id = integer("id").autoIncrement()
    val gameId = varchar("gameId", 6)
    val cardTypeId = short("cardTypeId")
    val curIndex = short("curIndex").nullable()
}

object CardStackContents : Table("\"cardStackContents\"") {
    val id = integer("id").autoIncrement()
    val stackId = integer("stackId")
    val index = short("index")
    val cardDefId = short("cardDefId")
}

object DrawnCards : Table("\"drawnCards\"") {
    val id = integer("id").autoIncrement()
    val gameId = varchar("gameId", 6)
    val cardTypeId = short("cardTypeId")
    val cardDefId = short("cardDefId")
}

object PlayerInventories : Table("\"playerInventories\"") {
    val id = integer("id").autoIncrement()
    val playerId = integer("playerId")
    val cardTypeId = short("cardTypeId")
    val cardDefId = short("cardDefId")
}

object DiceRolls : Table("\"diceRolls\"") {
    val id = integer("id").autoIncrement()
    val gameId = varchar("gameId", 6)
    val rolls = varchar("rolls", 15)
    val type = varchar("type", 16).nullable()
}

object Monsters : Table("\"monsters\"") {
    val id = integer("id").autoIncrement()
    val gameId = varchar("gameId", 6)
    val number = integer("number")
    val gridX = integer("gridX")
    val gridY = integer("gridY")

    override val primaryKey = PrimaryKey(id)
}
