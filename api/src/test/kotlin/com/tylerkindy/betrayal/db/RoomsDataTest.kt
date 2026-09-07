package com.tylerkindy.betrayal.db

import com.tylerkindy.betrayal.Direction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.ShortShrinker
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.ints
import io.kotest.property.exhaustive.map
import io.kotest.property.forAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.random.nextInt

val directionSets = Arb.set(Arb.enum<Direction>(), 0..Direction.values().size)
val rotations = Exhaustive.ints(0..3).map { it.toShort() }
val invalidRotationValues = (Short.MIN_VALUE..Short.MAX_VALUE) - (0..3)
val invalidRotations = arbitrary(listOf(Short.MIN_VALUE, -1, 4, Short.MAX_VALUE), ShortShrinker) {
    it.random.nextInt(invalidRotationValues.indices).let { i -> invalidRotationValues[i] }.toShort()
}

class RoomsDataTest : DescribeSpec({
    useDatabase()

    describe("returnRoomToStack") {
        it("returns to empty stack") {
            transaction {
                val gameId = "ABCDEF"
                val stackId =
                    RoomStacks.insert {
                        it[this.gameId] = gameId
                        it[curIndex] = null
                        it[flipped] = false
                    } get RoomStacks.id
                val roomId =
                    Rooms.insert {
                        it[this.gameId] = gameId
                        it[roomDefId] = 14
                        it[gridX] = 0
                        it[gridY] = 0
                        it[rotation] = 0
                    } get Rooms.id

                returnRoomToStack(gameId, roomId)

                Rooms.selectAll().shouldBeEmpty()

                val stackRows = RoomStacks.select { RoomStacks.id eq stackId }
                stackRows.count().shouldBe(1)
                stackRows.first().should {
                    it[RoomStacks.curIndex].shouldBe(0)
                }
                RoomStackContents.select { RoomStackContents.stackId eq stackId }
                    .first().should {
                        it[RoomStackContents.index].shouldBe(0)
                        it[RoomStackContents.roomDefId].shouldBe(14)
                    }
            }
        }

        it("returns to non-empty stack") {
            transaction {
                val gameId = "ABCDEF"
                val stackId = RoomStacks.insert {
                    it[this.gameId] = gameId
                    it[curIndex] = 17
                    it[flipped] = false
                } get RoomStacks.id
                RoomStackContents.insert {
                    it[this.stackId] = stackId
                    it[index] = 25
                    it[roomDefId] = 9
                }
                RoomStackContents.insert {
                    it[this.stackId] = stackId
                    it[index] = 17
                    it[roomDefId] = 13
                }
                RoomStackContents.insert {
                    it[this.stackId] = stackId
                    it[index] = 3
                    it[roomDefId] = 23
                }
                val roomId = Rooms.insert {
                    it[this.gameId] = gameId
                    it[roomDefId] = 14
                    it[gridX] = 0
                    it[gridY] = 0
                    it[rotation] = 0
                } get Rooms.id

                returnRoomToStack(gameId, roomId)

                Rooms.selectAll().shouldBeEmpty()
                RoomStackContents.selectAll()
                    .map { it[RoomStackContents.index] }
                    .shouldContainExactlyInAnyOrder(0, 1, 2, 3)
            }
        }
    }

    describe("rotateDoors") {
        it("maintains number of directions") {
            forAll(directionSets, rotations) { dirs, rotation ->
                rotateDoors(dirs, rotation).size == dirs.size
            }
        }

        it("throws for invalid rotations") {
            checkAll(directionSets, invalidRotations) { dirs, rotation ->
                shouldThrow<IllegalArgumentException> { rotateDoors(dirs, rotation) }
            }
        }

        it("returns the same directions with no rotation") {
            forAll(directionSets) { dirs ->
                rotateDoors(dirs, 0) == dirs
            }
        }

        it("always returns all directions") {
            val allDirections = Direction.values().toSet()
            forAll(rotations) { rotation ->
                rotateDoors(allDirections, rotation) == allDirections
            }
        }

        it("rotates doors") {
            rotateDoors(
                setOf(Direction.NORTH, Direction.WEST),
                3
            ) shouldBe setOf(Direction.NORTH, Direction.EAST)
        }
    }
})
