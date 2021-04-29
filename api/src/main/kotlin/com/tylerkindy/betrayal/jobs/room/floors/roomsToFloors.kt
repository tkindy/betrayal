package com.tylerkindy.betrayal.jobs.room.floors

import com.tylerkindy.betrayal.Floor
import com.tylerkindy.betrayal.GridLoc
import com.tylerkindy.betrayal.connectToDatabase
import com.tylerkindy.betrayal.db.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

fun main(argsArray: Array<String>) {
    val args = argsArray.toArgs()
    connectToDatabase()

    associateRoomsToFloors()
}

fun associateRoomsToFloors() {
    transaction {
        Games.selectAll().map(ResultRow::toGame)
    }
        .forEach {
            transaction {
                val rooms = Rooms.select { Rooms.gameId eq it.id }
                    .map(ResultRow::toDbRoom)

                associateToFloors(rooms).forEach { withFloor ->
                    Rooms.update(where = { Rooms.id eq withFloor.id }) {
                        it[floor] = withFloor.floor?.defEncoding?.toString()
                    }
                }
            }
        }
}

fun associateToFloors(rooms: List<DbRoom>): List<DbRoom> {
    return associateToFloorsHelp(rooms, null)
}

fun associateToFloorsHelp(rooms: List<DbRoom>, lastMap: RoomMap?): List<DbRoom> {
    val map = buildRoomMap(rooms)
    if (map == lastMap) {
        return rooms
    }

    return associateToFloorsHelp(
        rooms
            .map {
                if (it.floor != null) {
                    it
                } else {
                    it.copy(floor = associateToFloor(it, map))
                }
            },
        map
    )
}

fun associateToFloor(room: DbRoom, map: RoomMap): Floor? {
    val knownFloor = getFloor(room)
    if (knownFloor != null) {
        return knownFloor
    }

    val neighbors = getNeighbors(room, map)
    val floors = neighbors
        .map(::getFloor)
        .groupBy { it } - null

    return floors
        .maxByOrNull { it.value.size }
        ?.key
}

fun getNeighbors(room: DbRoom, map: RoomMap): List<DbRoom> {
    val neighborLocs: List<GridLoc> = ((room.gridX - 1)..(room.gridX + 1))
        .fold<Int, List<GridLoc>>(listOf()) { acc, x ->
            acc + ((room.gridY - 1)..(room.gridY + 1)).map { y ->
                GridLoc(x, y)
            }
        }
        .filter { (x, y) -> !(x == room.gridX && y == room.gridY) }

    return neighborLocs.mapNotNull { (x, y) -> map[x]?.get(y) }
}

fun getFloor(room: DbRoom): Floor? {
    return room.floor ?: startingRooms.firstOrNull {
        it.roomDefId == room.roomDefId
    }?.floor

}

fun buildRoomMap(rooms: List<DbRoom>): RoomMap =
    rooms.fold(emptyMap()) { acc, room ->
        val row = (acc[room.gridX] ?: emptyMap()) + (room.gridY to room)
        acc + (room.gridX to row)
    }

typealias RoomMap = Map<Int, Map<Int, DbRoom>>

data class Args(val write: Boolean)

fun Array<String>.toArgs() = Args(write = contains("--write"))
