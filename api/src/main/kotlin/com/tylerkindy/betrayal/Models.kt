package com.tylerkindy.betrayal

import com.tylerkindy.betrayal.defs.RollTable
import com.tylerkindy.betrayal.defs.Subtype
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("unused")
@Serializable
enum class CharacterColor {
    RED, YELLOW, GREEN, BLUE, WHITE, PURPLE
}

@Suppress("unused")
@Serializable
enum class TraitName {
    SPEED, MIGHT, SANITY, KNOWLEDGE
}

@Serializable
data class Trait(
    val value: Int,
    val index: Int,
    val scale: List<Int>,
    val startingIndex: Int
)

@Serializable
data class GridLoc(val gridX: Int, val gridY: Int)

@Serializable
data class Player(
    val id: Int,
    val name: String,
    val characterName: String,
    val color: CharacterColor,
    val loc: GridLoc,
    val speed: Trait,
    val might: Trait,
    val sanity: Trait,
    val knowledge: Trait,
    val cards: List<HeldCard>
)

@Suppress("unused")
@Serializable
enum class Floor(val defEncoding: Char) {
    BASEMENT('B'),
    GROUND('G'),
    UPPER('U'),
    ROOF('R');

    companion object {
        private val index = values().associateBy { it.defEncoding }

        fun parse(defEncoding: Char) = index[defEncoding]
            ?: throw IllegalArgumentException("No floor for encoding '$defEncoding'")
    }
}

@Serializable
enum class Direction(val defEncoding: Char) {
    NORTH('N'),
    SOUTH('S'),
    EAST('E'),
    WEST('W');

    companion object {
        private val index = values().associateBy { it.defEncoding }

        fun parse(defEncoding: Char) = index[defEncoding]
            ?: throw IllegalArgumentException("No direction for encoding '$defEncoding'")
    }
}

@Suppress("unused")
@Serializable
enum class Feature(val defEncoding: Char) {
    EVENT('E'),
    ITEM('I'),
    OMEN('O'),
    ANY('A'),
    DUMBWAITER('D');

    companion object {
        private val index = values().associateBy { it.defEncoding }

        fun parse(defEncoding: Char) = index[defEncoding]
            ?: throw IllegalArgumentException("No feature for encoding '$defEncoding'")
    }
}

@Suppress("unused")
@Serializable
enum class BarrierDirection(val defEncoding: Char) {
    VERTICAL('V'),
    HORIZONTAL('H');

    companion object {
        private val index = values().associateBy { it.defEncoding }

        fun parse(defEncoding: Char) = index[defEncoding]
            ?: throw IllegalArgumentException("No barrier direction for encoding '$defEncoding'")
    }
}

@Serializable
data class Barrier(
    val direction: BarrierDirection,
    val features: List<Feature>
)

@Serializable
data class Room(
    val id: Int,
    val name: String,
    val floors: Set<Floor>,
    val doorDirections: Set<Direction>,
    val features: List<Feature>,
    val loc: GridLoc,
    val description: String?,
    val barrier: Barrier?
)

@Serializable
data class StackRoom(
    val possibleFloors: Set<Floor>
)

@Serializable
data class FlippedRoom(
    val name: String,
    val doorDirections: Set<Direction>,
    val features: List<Feature>,
    val description: String?,
    val barrier: Barrier?
)

@Serializable
data class RoomStackResponse(
    val nextRoom: StackRoom? = null,
    val flippedRoom: FlippedRoom? = null
)

@Serializable
data class PlaceRoomResponse(
    val rooms: List<Room>,
    val nextRoom: StackRoom?
)

@Serializable
sealed class Card {
    @Serializable
    @SerialName("ITEM")
    data class ItemCard(
        val name: String,
        val subtype: Subtype?,
        val flavorText: String?,
        val description: String,
        val rollTable: RollTable?
    ) : Card()

    @Serializable
    @SerialName("EVENT")
    data class EventCard(
        val name: String,
        val condition: String?,
        val flavorText: String?,
        val description: String,
        val rollTable: RollTable?
    ) : Card()

    @Serializable
    @SerialName("OMEN")
    data class OmenCard(
        val name: String,
        val subtype: Subtype?,
        val flavorText: String?,
        val description: String,
        val rollTable: RollTable?
    ) : Card()
}

@Serializable
data class HeldCard(
    val id: Int,
    val card: Card
)

@Serializable
data class LobbyRequest(val hostName: String)

@Serializable
data class Lobby(val id: String)

@Serializable
data class Game(val id: String, val name: String)

@Serializable
data class Monster(
    val id: Int,
    val number: Int,
    val loc: GridLoc
)

@Serializable
enum class DiceRollType {
    AD_HOC, HAUNT
}

@Serializable
data class DiceRoll(
    val id: Int,
    val values: List<Int>,
    val type: DiceRollType
)
