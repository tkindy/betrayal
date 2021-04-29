package com.tylerkindy.betrayal.jobs.room.floors

import com.tylerkindy.betrayal.Floor
import com.tylerkindy.betrayal.db.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class RoomsToFloorsKtTest : DescribeSpec({
    useDatabase()

    describe("associateRoomsToFloors") {
        it("updates rooms with floors") {
            val gameId = "ABCDEF"
            transaction {
                Games.insert {
                    it[id] = gameId
                    it[name] = "foo"
                }
            }

            insertStartingRooms(gameId)
            transaction {
                Rooms.insert {
                    it[this.gameId] = gameId
                    it[roomDefId] = 35
                    it[gridX] = 2
                    it[gridY] = 11
                    it[rotation] = 0
                }
            }

            associateRoomsToFloors()

            transaction {
                Rooms.selectAll()
                    .map(ResultRow::toDbRoom)
                    .map { RoomFloor(it.roomDefId, it.floor) }
                    .shouldContainExactlyInAnyOrder(
                        RoomFloor(entranceHallDefId, Floor.GROUND),
                        RoomFloor(1, Floor.GROUND),
                        RoomFloor(2, Floor.GROUND),
                        RoomFloor(8, Floor.UPPER),
                        RoomFloor(10, Floor.ROOF),
                        RoomFloor(33, Floor.BASEMENT),
                        RoomFloor(35, Floor.BASEMENT)
                    )
            }
        }

        it("updates far rooms") {
            val gameId = "ABCDEF"
            transaction {
                Games.insert {
                    it[id] = gameId
                    it[name] = "foo"
                }
                Rooms.insert {
                    it[this.gameId] = gameId
                    it[roomDefId] = 100
                    it[gridX] = 0
                    it[gridY] = 0
                    it[rotation] = 0
                    it[floor] = Floor.UPPER.defEncoding.toString()
                }
                Rooms.insert {
                    it[this.gameId] = gameId
                    it[roomDefId] = 101
                    it[gridX] = 1
                    it[gridY] = 0
                    it[rotation] = 0
                }
                Rooms.insert {
                    it[this.gameId] = gameId
                    it[roomDefId] = 102
                    it[gridX] = 2
                    it[gridY] = 0
                    it[rotation] = 0
                }
            }

            associateRoomsToFloors()

            transaction {
                Rooms.selectAll()
                    .map(ResultRow::toDbRoom)
                    .map { RoomFloor(it.roomDefId, it.floor) }
                    .shouldContainExactlyInAnyOrder(
                        RoomFloor(100, Floor.UPPER),
                        RoomFloor(101, Floor.UPPER),
                        RoomFloor(102, Floor.UPPER)
                    )
            }
        }
    }
})

data class RoomFloor(val roomDefId: Short, val floor: Floor?)
