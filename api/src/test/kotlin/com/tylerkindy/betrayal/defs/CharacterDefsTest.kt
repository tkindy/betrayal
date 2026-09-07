package com.tylerkindy.betrayal.defs

import com.tylerkindy.betrayal.CharacterColor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class CharacterDefsTest : DescribeSpec({
    describe("characters") {
        it("parses correctly") {
            characters[2] shouldBe CharacterDefinition(
                id = 2,
                name = "Missy Dubourde",
                color = CharacterColor.YELLOW,
                speed = TraitDefinition(listOf(3, 4, 5, 6, 6, 6, 7, 7), 2),
                might = TraitDefinition(listOf(2, 3, 3, 3, 4, 5, 6, 7), 3),
                sanity = TraitDefinition(listOf(1, 2, 3, 4, 5, 5, 6, 7), 2),
                knowledge = TraitDefinition(listOf(2, 3, 4, 4, 5, 6, 6, 6), 3)
            )
        }
    }

    describe("parseTraitDefinition") {
        it("works") {
            parseTraitDefinition("2,2,3,5*,5,5,6,6") shouldBe TraitDefinition(
                scale = listOf(2, 2, 3, 5, 5, 5, 6, 6),
                startingIndex = 3
            )
        }

        it("catches missing starting value") {
            shouldThrow<IllegalArgumentException> { parseTraitDefinition("2,3,4,4,4,6,6") }
        }

        it("catches duplicate starting values") {
            shouldThrow<IllegalArgumentException> { parseTraitDefinition("2,3*,4,4,4*,6,6") }
        }

        it("catches empty value") {
            shouldThrow<IllegalArgumentException> { parseTraitDefinition("") }
        }
    }
})
