package com.tylerkindy.betrayal.db

import com.tylerkindy.betrayal.HeldCard
import com.tylerkindy.betrayal.defs.CardType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

fun getPlayerInventory(gameId: String, playerId: Int): List<HeldCard> {
    return transaction {
        assertPlayerInGame(gameId, playerId)

        PlayerInventories.select { PlayerInventories.playerId eq playerId }
            .map {
                HeldCard(
                    id = it[PlayerInventories.id],
                    card = buildCard(
                        CardType.fromId(
                            it[PlayerInventories.cardTypeId]
                        ),
                        it[PlayerInventories.cardDefId]
                    )
                )
            }
    }
}

fun discardHeldCard(gameId: String, playerId: Int, cardId: Int) {
    transaction {
        assertPlayerInGame(gameId, playerId)

        PlayerInventories.deleteWhere {
            (PlayerInventories.playerId eq playerId) and (PlayerInventories.id eq cardId)
        }
    }
}

fun giveHeldCardToPlayer(gameId: String, fromPlayerId: Int, cardId: Int, toPlayerId: Int) {
    transaction {
        assertPlayerInGame(gameId, fromPlayerId)
        assertPlayerInGame(gameId, toPlayerId)

        val cardRow = PlayerInventories.select {
            (PlayerInventories.id eq cardId) and (PlayerInventories.playerId eq fromPlayerId)
        }
            .firstOrNull() ?: throw IllegalArgumentException("Player $fromPlayerId has no card $cardId")

        PlayerInventories.deleteWhere { PlayerInventories.id eq cardId }
        PlayerInventories.insert {
            it[playerId] = toPlayerId
            it[cardTypeId] = cardRow[cardTypeId]
            it[cardDefId] = cardRow[cardDefId]
        }
    }
}

fun assertPlayerInGame(gameId: String, playerId: Int) {
    Players.select { (Players.id eq playerId) and (Players.gameId eq gameId) }
        .firstOrNull() ?: throw IllegalArgumentException("Player $playerId is not part of game $gameId")
}
