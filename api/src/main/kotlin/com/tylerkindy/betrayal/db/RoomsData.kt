package com.tylerkindy.betrayal.db

import com.tylerkindy.betrayal.Direction
import com.tylerkindy.betrayal.GridLoc
import com.tylerkindy.betrayal.Room
import com.tylerkindy.betrayal.defs.rooms
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun ResultRow.toRoom(): Room {
    val roomDefId = this[Rooms.roomDefId]
    val roomDef = rooms[roomDefId]
        ?: error("No room defined with ID $roomDefId")

    return Room(
        id = this[Rooms.id],
        name = roomDef.name,
        floors = roomDef.floors,
        doorDirections = rotateDoors(roomDef.doors, this[Rooms.rotation]),
        features = roomDef.features,
        loc = GridLoc(gridX = this[Rooms.gridX], gridY = this[Rooms.gridY]),
        description = roomDef.description,
        barrier = roomDef.barrier
    )
}

fun getRooms(gameId: String): List<Room> {
    return transaction {
        Rooms.select { Rooms.gameId eq gameId }
            .map { it.toRoom() }
    }
}

private data class StartingRoom(
    val roomDefId: Short,
    val loc: GridLoc
)

val entranceHallLoc = GridLoc(4, 3)
const val entranceHallDefId: Short = 0
private val startingRooms = listOf(
    // Entrance Hall
    StartingRoom(entranceHallDefId, entranceHallLoc),
    // Foyer
    StartingRoom(1, GridLoc(3, 3)),
    // Grand Staircase
    StartingRoom(2, GridLoc(2, 3)),
    // Upper Landing
    StartingRoom(8, GridLoc(-8, -8)),
    // Roof Landing
    StartingRoom(10, GridLoc(10, -8)),
    // Basement Landing
    StartingRoom(33, GridLoc(2, 12))
)
val startingRoomIds = startingRooms.map { it.roomDefId }

fun insertStartingRooms(gameId: String) {
    transaction {
        Rooms.batchInsert(startingRooms) { (roomDefId, loc) ->
            this[Rooms.gameId] = gameId
            this[Rooms.roomDefId] = roomDefId
            this[Rooms.gridX] = loc.gridX
            this[Rooms.gridY] = loc.gridY
            this[Rooms.rotation] = 0
        }
    }
}

private val rotated = mapOf(
    Direction.EAST to Direction.NORTH,
    Direction.NORTH to Direction.WEST,
    Direction.WEST to Direction.SOUTH,
    Direction.SOUTH to Direction.EAST
)

internal fun rotateDoors(directions: Set<Direction>, rotation: Short): Set<Direction> {
    if (!(0..3).contains(rotation)) {
        throw IllegalArgumentException("Invalid rotation $rotation")
    }
    if (directions.size == Direction.values().size) {
        return directions
    }

    return directions.map { direction ->
        (0 until rotation).fold(direction) { dir, _ -> rotated[dir]!! }
    }.toSet()
}

fun getRoomAtLoc(gameId: String, loc: GridLoc): Room? {
    return transaction {
        Rooms.select {
            (Rooms.gameId eq gameId) and
                    (Rooms.gridX eq loc.gridX) and
                    (Rooms.gridY eq loc.gridY)
        }.firstOrNull()?.toRoom()
    }
}

fun moveRoom(gameId: String, roomId: Int, loc: GridLoc) {
    transaction {
        assertRoomInGame(gameId, roomId)
        if (getRoomAtLoc(gameId, loc) != null) {
            throw IllegalArgumentException("Can't place room in occupied spot")
        }

        val originalLoc = Rooms.slice(Rooms.gridX, Rooms.gridY)
            .select { Rooms.id eq roomId }
            .first()
            .let {
                GridLoc(
                    gridX = it[Rooms.gridX],
                    gridY = it[Rooms.gridY]
                )
            }

        Rooms.update(where = { Rooms.id eq roomId }) {
            it[gridX] = loc.gridX
            it[gridY] = loc.gridY
        }
        Players.update(
            where = {
                (Players.gameId eq gameId) and
                        (Players.gridX eq originalLoc.gridX) and
                        (Players.gridY eq originalLoc.gridY)
            }
        ) {
            it[gridX] = loc.gridX
            it[gridY] = loc.gridY
        }
        Monsters.update(
            where = {
                (Monsters.gameId eq gameId) and
                        (Monsters.gridX eq originalLoc.gridX) and
                        (Monsters.gridY eq originalLoc.gridY)
            }
        ) {
            it[gridX] = loc.gridX
            it[gridY] = loc.gridY
        }
    }
}

fun rotateRoom(gameId: String, roomId: Int) {
    transaction {
        assertRoomInGame(gameId, roomId)

        val curRotation = Rooms.slice(Rooms.rotation)
            .select { Rooms.id eq roomId }
            .first()[Rooms.rotation]

        Rooms.update(where = { Rooms.id eq roomId }) {
            it[rotation] = rotate(curRotation)
        }
    }
}

fun returnRoomToStack(gameId: String, roomId: Int) {
    transaction {
        val room = assertRoomInGame(gameId, roomId)

        if (startingRoomIds.contains(room.roomDefId)) {
            throw IllegalArgumentException(
                "Can't return room $roomId since it's a starting room"
            )
        }
        if (containsPlayers(room) || containsMonsters(room)) {
            throw IllegalArgumentException(
                "Can't return room $roomId since it contains players or monsters"
            )
        }

        Rooms.deleteWhere { Rooms.id eq room.id }
        addToEndOfRoomStack(gameId, room.roomDefId)
        shuffleRoomStack(gameId)
    }
}

fun assertRoomInGame(gameId: String, roomId: Int): DbRoom =
    Rooms.select { (Rooms.id eq roomId) and (Rooms.gameId eq gameId) }
        .firstOrNull()
        ?.toDbRoom()
        ?: throw IllegalArgumentException("Room $roomId not in game $gameId")

fun containsPlayers(room: DbRoom) =
    Players.select {
        (Players.gameId eq room.gameId) and
                (Players.gridX eq room.gridX) and
                (Players.gridY eq room.gridY)
    }
        .any()

fun containsMonsters(room: DbRoom) =
    Monsters.select {
        (Monsters.gameId eq room.gameId) and
                (Monsters.gridX eq room.gridX) and
                (Monsters.gridY eq room.gridY)
    }
        .any()

data class DbRoom(
    val id: Int,
    val gameId: String,
    val roomDefId: Short,
    val gridX: Int,
    val gridY: Int,
    val rotation: Short
)

fun ResultRow.toDbRoom() =
    DbRoom(
        id = this[Rooms.id],
        gameId = this[Rooms.gameId],
        roomDefId = this[Rooms.roomDefId],
        gridX = this[Rooms.gridX],
        gridY = this[Rooms.gridY],
        rotation = this[Rooms.rotation]
    )
