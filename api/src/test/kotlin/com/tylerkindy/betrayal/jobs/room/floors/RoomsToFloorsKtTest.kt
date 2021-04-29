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
    }
})

data class RoomFloor(val roomDefId: Short, val floor: Floor?)
