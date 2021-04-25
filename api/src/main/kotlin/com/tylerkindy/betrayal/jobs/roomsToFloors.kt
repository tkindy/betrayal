package com.tylerkindy.betrayal.jobs

import com.tylerkindy.betrayal.Game
import com.tylerkindy.betrayal.connectToDatabase
import com.tylerkindy.betrayal.db.Games
import com.tylerkindy.betrayal.db.toGame
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

fun main() {
    connectToDatabase()

    Games.selectAll().map(ResultRow::toGame).forEach(::associateRoomsToFloors)
}

fun associateRoomsToFloors(game: Game) {
    
}
