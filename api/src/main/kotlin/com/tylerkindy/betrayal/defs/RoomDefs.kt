package com.tylerkindy.betrayal.defs

import com.tylerkindy.betrayal.*
import com.tylerkindy.betrayal.db.startingRoomIds
import org.apache.commons.csv.CSVFormat
import java.io.InputStreamReader

data class RoomDefinition(
    val id: Short,
    val name: String,
    val floors: Set<Floor>,
    val doors: Set<Direction>,
    val features: List<Feature>,
    val description: String?,
    val barrier: Barrier?
)

private val fileStream = RoomDefinition::class.java.getResourceAsStream("/rooms.csv")
val rooms = CSVFormat.DEFAULT.builder().setNullString("").setHeader().build()
    .parse(InputStreamReader(fileStream))
    .map {
        RoomDefinition(
            id = it["id"].toShort(),
            name = it["name"],
            floors = it["floors"].map(Floor.Companion::parse).toSet(),
            doors = it["doors"].map(Direction.Companion::parse).toSet(),
            features = it["features"]?.map(Feature.Companion::parse) ?: emptyList(),
            description = it["description"],
            barrier = parseBarrier(it["barrier"], it["barrierFeatures"])
        )
    }
    .associateBy { it.id }
val initialStackRooms = rooms.values.filter { it.id !in startingRoomIds }

fun parseBarrier(direction: String?, features: String?): Barrier? {
    if (direction == null) {
        return null
    }

    return Barrier(
        direction = BarrierDirection.parse(direction[0]),
        features = features?.map(Feature.Companion::parse) ?: emptyList()
    )
}
