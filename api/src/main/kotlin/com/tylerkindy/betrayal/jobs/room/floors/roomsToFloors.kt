package com.tylerkindy.betrayal.jobs.room.floors

import com.tylerkindy.betrayal.connectToDatabase
import com.tylerkindy.betrayal.db.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun main(argsArray: Array<String>) {
    val args = argsArray.toArgs()
    connectToDatabase()

    transaction {
        Games.selectAll()
    }
        .map(ResultRow::toGame)
        .forEach {
            val rooms = Rooms.select { Rooms.gameId eq it.id }
                .map(ResultRow::toDbRoom)

            associateToFloors(rooms)
        }
}

fun associateToFloors(rooms: List<DbRoom>): List<DbRoom> {
    
}

data class Args(val write: Boolean)

fun Array<String>.toArgs() = Args(write = contains("--write"))
