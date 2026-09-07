package com.tylerkindy.betrayal.db

import com.tylerkindy.betrayal.Card
import com.tylerkindy.betrayal.Player
import com.tylerkindy.betrayal.defs.CardType
import com.tylerkindy.betrayal.defs.events
import com.tylerkindy.betrayal.defs.items
import com.tylerkindy.betrayal.defs.omens
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

fun getDrawnCard(gameId: String): Card? {
    return transaction {
        DrawnCards.select { DrawnCards.gameId eq gameId }
            .firstOrNull()
            ?.let { row ->
                buildCard(CardType.fromId(row[DrawnCards.cardTypeId]), row[DrawnCards.cardDefId])
            }
    }
}

fun discardDrawnCard(gameId: String) {
    transaction {
        DrawnCards.deleteWhere { DrawnCards.gameId eq gameId }
    }
}

fun giveDrawnCardToPlayer(gameId: String, playerId: Int): Player {
    return transaction {
        val card = DrawnCards.select { DrawnCards.gameId eq gameId }
            .firstOrNull()
            ?: throw IllegalStateException("No currently drawn card")

        assertPlayerInGame(gameId, playerId)

        PlayerInventories.insert {
            it[this.playerId] = playerId
            it[cardTypeId] = card[DrawnCards.cardTypeId]
            it[cardDefId] = card[DrawnCards.cardDefId]
        }
        DrawnCards.deleteWhere { DrawnCards.gameId eq gameId }

        getPlayer(gameId, playerId)
    }
}

fun buildCard(cardType: CardType, cardDefId: Short): Card {
    return when (cardType) {
        CardType.EVENT -> events[cardDefId]!!.toCard()
        CardType.ITEM -> items[cardDefId]!!.toCard()
        CardType.OMEN -> omens[cardDefId]!!.toCard()
    }
}
