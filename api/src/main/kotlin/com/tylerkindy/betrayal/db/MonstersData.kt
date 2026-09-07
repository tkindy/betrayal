package com.tylerkindy.betrayal.db

import com.tylerkindy.betrayal.GridLoc
import com.tylerkindy.betrayal.Monster
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun getMonsters(gameId: String): List<Monster> {
    return transaction {
        Monsters.select { Monsters.gameId eq gameId }
            .map {
                Monster(
                    id = it[Monsters.id],
                    number = it[Monsters.number],
                    loc = GridLoc(
                        gridX = it[Monsters.gridX],
                        gridY = it[Monsters.gridY]
                    )
                )
            }
    }
}

fun addMonster(gameId: String): Monster {
    return transaction {
        val prevNumber = Monsters.select { Monsters.gameId eq gameId }
            .orderBy(Monsters.number, SortOrder.DESC)
            .firstOrNull()
            ?.get(Monsters.number)
            ?: 0
        val number = prevNumber + 1

        val loc = Rooms.slice(Rooms.gridX, Rooms.gridY)
            .select { (Rooms.gameId eq gameId) and (Rooms.roomDefId eq entranceHallDefId) }
            .first()
            .let {
                GridLoc(
                    gridX = it[Rooms.gridX],
                    gridY = it[Rooms.gridY]
                )
            }

        val id = Monsters.insert {
            it[this.gameId] = gameId
            it[this.number] = number
            it[this.gridX] = loc.gridX
            it[this.gridY] = loc.gridY
        } get Monsters.id

        Monster(
            id = id,
            number = number,
            loc = loc
        )
    }
}

fun moveMonster(gameId: String, monsterId: Int, loc: GridLoc): Monster {
    return transaction {
        assertMonsterInGame(gameId, monsterId)

        Monsters.update(where = { Monsters.id eq monsterId }) {
            it[this.gridX] = loc.gridX
            it[this.gridY] = loc.gridY
        }

        Monsters.select { Monsters.id eq monsterId }
            .first().toMonster()
    }
}

fun assertMonsterInGame(gameId: String, monsterId: Int) {
    Monsters.select { (Monsters.id eq monsterId) and (Monsters.gameId eq gameId) }
        .firstOrNull()
        ?: throw IllegalArgumentException("Monster $monsterId is not in game $gameId")
}

fun ResultRow.toMonster(): Monster =
    Monster(
        id = this[Monsters.id],
        number = this[Monsters.number],
        loc = GridLoc(
            gridX = this[Monsters.gridX],
            gridY = this[Monsters.gridY]
        )
    )
