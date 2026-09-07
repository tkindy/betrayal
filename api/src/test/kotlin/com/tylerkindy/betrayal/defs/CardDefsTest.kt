package com.tylerkindy.betrayal.defs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CardDefsTest : StringSpec({
    "parsing of RangeRollTargets" {
        val target = RollTarget.parse("3-5")

        target shouldBe RollTarget.RangeRollTarget(3..5)
        target.matches(2) shouldBe false
        target.matches(3) shouldBe true
        target.matches(4) shouldBe true
        target.matches(5) shouldBe true
        target.matches(6) shouldBe false
    }

    "serialization of ExactRollTargets" {
        val target: RollTarget = RollTarget.ExactRollTarget(7)

        Json.encodeToString(target) shouldBe
                """
                {"type":"EXACT","target":7}
            """.trimIndent()
    }

    "serialization of RangeRollTargets" {
        val target: RollTarget = RollTarget.RangeRollTarget(3..5)

        Json.encodeToString(target) shouldBe
                """
                {"type":"RANGE","start":3,"end":5}
            """.trimIndent()
    }

    "serialization of MinRollTargets" {
        val target: RollTarget = RollTarget.MinRollTarget(3)

        Json.encodeToString(target) shouldBe
                """
                {"type":"MIN","minimum":3}
            """.trimIndent()
    }

    "parsing of events" {
        events[3] shouldBe EventDefinition(
            id = 3,
            name = "Phone Call",
            condition = null,
            flavorText = "A phone rings in the room. You feel compelled to answer it.",
            description = "Roll 2 dice. A sweet little granny voice says:\n<rollTable>",
            rollTable = listOf(
                RollTableRow(
                    RollTarget.ExactRollTarget(4),
                    "“Tea and cakes! Tea and cakes! You always were my favorite!” Gain 1 Sanity."
                ),
                RollTableRow(
                    RollTarget.ExactRollTarget(3),
                    "“I’m always here for you, Pattycakes. Watching…” Gain 1 Knowledge."
                ),
                RollTableRow(
                    RollTarget.RangeRollTarget(1..2),
                    "“I’m here, Sweetums! Give us a kiss!” Take 1 die of mental damage."
                ),
                RollTableRow(
                    RollTarget.ExactRollTarget(0),
                    "“Bad little children must be punished!” Take 2 dice of physical damage."
                )
            )
        )
    }

    "parsing of items" {
        items[2] shouldBe ItemDefinition(
            id = 2,
            name = "Bell",
            subtype = null,
            flavorText = "A brass bell that makes a resonant clang.",
            description = """
                Gain 1 Sanity now.
                Lose 1 Sanity if you lose the Bell.
                Once during your turn after the haunt is revealed, you can attempt a Sanity roll to use the Bell:
                <rollTable>""".trimIndent(),
            rollTable = listOf(
                RollTableRow(
                    RollTarget.MinRollTarget(5),
                    "Move any number of unimpeded heroes 1 space closer to you."
                ),
                RollTableRow(
                    RollTarget.RangeRollTarget(0..4),
                    "The traitor can move any number of monsters 1 space closer to you. (If you are the traitor, this result has no effect.) If there is no traitor, all monsters move 1 space closer to you."
                )
            )
        )
    }

    "parsing of omens" {
        omens[5] shouldBe OmenDefinition(
            id = 5,
            name = "Cat",
            subtype = Subtype.COMPANION,
            flavorText = "It crossed your path, and you’re supposed to have bad luck. But maybe you crossed its path, and it’s the one that isn’t too happy about it. Now it accompanies you.",
            description = """
                You may call on the Cat for luck before your trait roll. Roll 1 die:
                <rollTable>
                This omen cannot be dropped, traded, or stolen.""".trimIndent(),
            rollTable = listOf(
                RollTableRow(
                    RollTarget.ExactRollTarget(0),
                    "Subtract 2 from the result of that trait roll."
                ),
                RollTableRow(
                    RollTarget.MinRollTarget(1),
                    "Add 2 to the result of the trait roll."
                )
            )
        )
    }
})
