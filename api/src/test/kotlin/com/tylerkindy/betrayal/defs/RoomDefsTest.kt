package com.tylerkindy.betrayal.defs

import com.tylerkindy.betrayal.Direction
import com.tylerkindy.betrayal.Feature
import com.tylerkindy.betrayal.Floor
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class RoomDefsTest : DescribeSpec({
    describe("rooms") {
        it("parses") {
            rooms[3] shouldBe RoomDefinition(
                id = 3,
                name = "Nursery",
                floors = setOf(Floor.UPPER, Floor.ROOF),
                doors = setOf(Direction.NORTH, Direction.EAST),
                features = listOf(Feature.OMEN),
                description = "If you end your turn here, gain 1 Sanity if it’s below its starting value. Lose 1 Sanity if it’s above its starting value.",
                barrier = null
            )
        }

        it("keeps roll tables readable as plain text") {
            rooms[36]?.description shouldBe """
                Once per turn, roll 2 dice and move this room next to any open door on:
                4  Any floor
                3  Upper floor
                2  Ground floor
                1  Basement
                0  Basement, then take 1 die of physical damage
            """.trimIndent()
        }
    }
})
