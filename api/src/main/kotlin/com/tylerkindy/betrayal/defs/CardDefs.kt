package com.tylerkindy.betrayal.defs

import com.tylerkindy.betrayal.Card
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.apache.commons.csv.CSVFormat
import java.io.InputStreamReader

@Serializable
sealed class RollTarget {
    @Serializable
    @SerialName("EXACT")
    data class ExactRollTarget(val target: Int) : RollTarget() {
        override fun matches(roll: Int): Boolean {
            return target == roll
        }
    }

    @Serializable(with = RangeRollTarget.Serializer::class)
    @SerialName("RANGE")
    data class RangeRollTarget(val targetRange: IntRange) : RollTarget() {
        override fun matches(roll: Int): Boolean {
            return targetRange.contains(roll)
        }

        object Serializer : KSerializer<RangeRollTarget> {
            override val descriptor: SerialDescriptor = RangeSurrogate.serializer().descriptor

            override fun serialize(encoder: Encoder, value: RangeRollTarget) {
                encoder.encodeSerializableValue(
                    RangeSurrogate.serializer(),
                    RangeSurrogate(start = value.targetRange.first, end = value.targetRange.last)
                )
            }

            override fun deserialize(decoder: Decoder): RangeRollTarget {
                val surrogate = decoder.decodeSerializableValue(RangeSurrogate.serializer())
                return RangeRollTarget(targetRange = surrogate.start..surrogate.end)
            }

            @Serializable
            @SerialName("RANGE")
            private class RangeSurrogate(val start: Int, val end: Int)
        }
    }

    @Serializable
    @SerialName("MIN")
    data class MinRollTarget(val minimum: Int) : RollTarget() {
        override fun matches(roll: Int): Boolean {
            return roll >= minimum
        }
    }

    abstract fun matches(roll: Int): Boolean

    companion object {
        private val EXACT_REGEX = Regex("(\\d+)")
        private val RANGE_REGEX = Regex("(\\d+)-(\\d+)")
        private val MIN_REGEX = Regex("(\\d+)\\+")

        fun parse(s: String): RollTarget {
            return EXACT_REGEX.matchEntire(s)
                ?.let { ExactRollTarget(it.groupValues[1].toInt()) }
                ?: RANGE_REGEX.matchEntire(s)
                    ?.let {
                        RangeRollTarget(
                            IntRange(
                                it.groupValues[1].toInt(),
                                it.groupValues[2].toInt()
                            )
                        )
                    }
                ?: MIN_REGEX.matchEntire(s)
                    ?.let { MinRollTarget(it.groupValues[1].toInt()) }
                ?: throw IllegalArgumentException("Invalid roll target: '$s'")
        }
    }
}

@Serializable
data class RollTableRow(
    val target: RollTarget,
    val outcome: String
)
typealias RollTable = List<RollTableRow>

private val ROW_REGEX = Regex("([^\\s]+) {2}(.*)")
fun parseRollTable(s: String?): RollTable? {
    return s?.split('\n')
        ?.map { ROW_REGEX.matchEntire(it) ?: throw IllegalArgumentException("Invalid row '$it'") }
        ?.map {
            RollTableRow(
                target = RollTarget.parse(it.groupValues[1]),
                outcome = it.groupValues[2]
            )
        }
}

data class EventDefinition(
    val id: Short,
    val name: String,
    val condition: String?,
    val flavorText: String?,
    val description: String,
    val rollTable: RollTable?
) {
    fun toCard(): Card.EventCard {
        return Card.EventCard(
            name = name,
            condition = condition,
            flavorText = flavorText,
            description = description,
            rollTable = rollTable
        )
    }
}

@Serializable
enum class Subtype {
    COMPANION, WEAPON
}

data class ItemDefinition(
    val id: Short,
    val name: String,
    val subtype: Subtype?,
    val flavorText: String?,
    val description: String,
    val rollTable: RollTable?
) {
    fun toCard(): Card.ItemCard {
        return Card.ItemCard(
            name = name,
            subtype = subtype,
            flavorText = flavorText,
            description = description,
            rollTable = rollTable
        )
    }
}

data class OmenDefinition(
    val id: Short,
    val name: String,
    val subtype: Subtype?,
    val flavorText: String?,
    val description: String,
    val rollTable: RollTable?
) {
    fun toCard(): Card.OmenCard {
        return Card.OmenCard(
            name = name,
            subtype = subtype,
            flavorText = flavorText,
            description = description,
            rollTable = rollTable
        )
    }
}

val csvFormat: CSVFormat =
    CSVFormat.DEFAULT.builder().setNullString("").setHeader().build()

val events = csvFormat
    .parse(InputStreamReader(EventDefinition::class.java.getResourceAsStream("/events.csv")))
    .map {
        EventDefinition(
            id = it["id"].toShort(),
            name = it["name"],
            condition = it["condition"],
            flavorText = it["flavorText"],
            description = it["description"],
            rollTable = parseRollTable(it["rollTable"])
        )
    }
    .associateBy { it.id }
val items = csvFormat
    .parse(InputStreamReader(ItemDefinition::class.java.getResourceAsStream("/items.csv")))
    .map {
        ItemDefinition(
            id = it["id"].toShort(),
            name = it["name"],
            subtype = it["subtype"]?.let(Subtype::valueOf),
            flavorText = it["flavorText"],
            description = it["description"],
            rollTable = parseRollTable(it["rollTable"])
        )
    }
    .associateBy { it.id }
val omens = csvFormat
    .parse(InputStreamReader(OmenDefinition::class.java.getResourceAsStream("/omens.csv")))
    .map {
        OmenDefinition(
            id = it["id"].toShort(),
            name = it["name"],
            subtype = it["subtype"]?.let(Subtype::valueOf),
            flavorText = it["flavorText"],
            description = it["description"],
            rollTable = parseRollTable(it["rollTable"])
        )
    }
    .associateBy { it.id }

enum class CardType(val id: Short) {
    EVENT(0),
    ITEM(1),
    OMEN(2);

    companion object {
        fun fromId(id: Short): CardType {
            return values().first { it.id == id }
        }
    }
}
