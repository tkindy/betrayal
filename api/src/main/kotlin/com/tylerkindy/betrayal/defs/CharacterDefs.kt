package com.tylerkindy.betrayal.defs

import com.tylerkindy.betrayal.CharacterColor
import com.tylerkindy.betrayal.Trait
import org.apache.commons.csv.CSVFormat
import java.io.InputStreamReader

data class TraitDefinition(val scale: List<Int>, val startingIndex: Int) {
    fun toTrait(index: Int = startingIndex) =
        Trait(
            value = scale[index],
            index = index,
            scale = scale,
            startingIndex = startingIndex
        )
}

data class CharacterDefinition(
    val id: Short,
    val name: String,
    val color: CharacterColor,
    val speed: TraitDefinition,
    val might: TraitDefinition,
    val sanity: TraitDefinition,
    val knowledge: TraitDefinition
)

private val fileStream = CharacterDefinition::class.java.getResourceAsStream("/characters.csv")
val characters = CSVFormat.DEFAULT.builder().setHeader().build()
    .parse(InputStreamReader(fileStream))
    .map {
        CharacterDefinition(
            id = it["id"].toShort(),
            name = it["name"],
            color = CharacterColor.valueOf(it["color"]),
            speed = parseTraitDefinition(it["speed"]),
            might = parseTraitDefinition(it["might"]),
            sanity = parseTraitDefinition(it["sanity"]),
            knowledge = parseTraitDefinition(it["knowledge"])
        )
    }
    .associateBy { it.id }

internal fun parseTraitDefinition(value: String): TraitDefinition {
    data class Acc(val scale: List<Int> = emptyList(), val startingIndex: Int? = null)

    return value.split(",")
        .foldIndexed(Acc()) { i, acc, scaleItem ->
            var startingIndex = acc.startingIndex

            if (scaleItem.endsWith("*")) {
                if (startingIndex != null) {
                    throw IllegalArgumentException("Multiple starting values in $value")
                }
                startingIndex = i
            }

            Acc(acc.scale + scaleItem.substringBefore("*").toInt(), startingIndex)
        }
        .let {
            TraitDefinition(
                scale = it.scale,
                startingIndex = it.startingIndex
                    ?: throw IllegalArgumentException("No starting value set in $value")
            )
        }
}
