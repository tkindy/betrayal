package com.tylerkindy.betrayal.routes

import com.tylerkindy.betrayal.*
import com.tylerkindy.betrayal.db.CardStackContents
import com.tylerkindy.betrayal.db.CardStacks
import com.tylerkindy.betrayal.db.Games
import com.tylerkindy.betrayal.db.Players
import com.tylerkindy.betrayal.defs.CardType
import com.tylerkindy.betrayal.defs.events
import com.tylerkindy.betrayal.defs.items
import com.tylerkindy.betrayal.defs.omens
import com.tylerkindy.betrayal.routes.GameClientMessage.NameMessage
import com.tylerkindy.betrayal.routes.GameClientMessage.SearchCardStack
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

val gameRoutes: Routing.() -> Unit = {
    route("games") {
        route("{gameId}") {
            get {
                val gameId = call.parameters["gameId"]!!
                val game = transaction {
                    Games.select { Games.id eq gameId }
                        .firstOrNull()
                        ?.let {
                            Game(id = it[Games.id], name = it[Games.name])
                        }
                } ?: return@get call.respond(HttpStatusCode.NotFound)

                call.respond(game)
            }
            roomRoutes()
            playerRoutes()
            monsterRoutes()
            roomStackRoutes()
            cardRoutes()
            diceRollRoutes()

            webSocket {
                val gameId = call.parameters["gameId"]!!

                val (name) = parseMessage<GameClientMessage>(incoming.receive())
                        as? NameMessage
                    ?: return@webSocket close(
                        CloseReason(
                            CloseReason.Codes.VIOLATED_POLICY,
                            "Expected name message"
                        )
                    )

                transaction {
                    Players.select { (Players.gameId eq gameId) and (Players.name eq name) }
                        .firstOrNull()
                }
                    ?: return@webSocket close(
                        CloseReason(
                            CloseReason.Codes.VIOLATED_POLICY,
                            "Player $name is not part of this game"
                        )
                    )

                launch {
                    gameUpdateManager.getUpdates(gameId).collect { update ->
                        send(Json.encodeToString(update as GameServerMessage))
                    }
                }

                for (frame in incoming) {
                    val message = parseMessage<GameClientMessage>(frame)
                        ?: return@webSocket close(
                            CloseReason(
                                CloseReason.Codes.VIOLATED_POLICY,
                                "Unexpected client message"
                            )
                        )

                    when (message) {
                        is SearchCardStack -> {
                            searchCardStack(gameId, message.cardType)
                        }

                        is NameMessage -> {
                            return@webSocket close(
                                CloseReason(
                                    CloseReason.Codes.VIOLATED_POLICY,
                                    "Unexpected name message"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

suspend fun WebSocketSession.searchCardStack(gameId: String, type: CardType) {
    val cardDefs = when (type) {
        CardType.EVENT -> events
        CardType.ITEM -> items
        CardType.OMEN -> omens
    }

    val cards = transaction {
        CardStackContents.join(CardStacks, JoinType.INNER, CardStackContents.stackId, CardStacks.id)
            .select { (CardStacks.gameId eq gameId) and (CardStacks.cardTypeId eq type.id) }
            .map { row ->
                cardDefs[row[CardStackContents.cardDefId]]!!.toCard()
            }
    }
        .sortedBy { it.name }

    send(Json.encodeToString(GameServerMessage.CardStackContents(cards) as GameServerMessage))
}

@Serializable
sealed class GameClientMessage {
    @Serializable
    @SerialName("name")
    data class NameMessage(val name: String) : GameClientMessage()

    @Serializable
    @SerialName("search-card-stack")
    data class SearchCardStack(val cardType: CardType) : GameClientMessage()
}

@Serializable
sealed class GameServerMessage {
    @Serializable
    @SerialName("state")
    data class GameUpdate(
        val rooms: List<Room>,
        val players: List<Player>,
        val roomStack: RoomStackResponse,
        val drawnCard: Card?,
        val latestRoll: DiceRoll?,
        val monsters: List<Monster>
    ) : GameServerMessage()

    @Serializable
    @SerialName("card-stack-contents")
    data class CardStackContents(val contents: List<Card>) : GameServerMessage()
}
