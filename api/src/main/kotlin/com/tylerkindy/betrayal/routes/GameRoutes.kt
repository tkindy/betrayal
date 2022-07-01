package com.tylerkindy.betrayal.routes

import com.tylerkindy.betrayal.*
import com.tylerkindy.betrayal.db.Games
import com.tylerkindy.betrayal.db.Players
import com.tylerkindy.betrayal.defs.CardType
import com.tylerkindy.betrayal.routes.GameClientMessage.NameMessage
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
                }
            }
        }
    }
}

@Serializable
sealed class GameClientMessage {
    @Serializable
    @SerialName("name")
    data class NameMessage(val name: String) : GameClientMessage()

    @Serializable
    @SerialName("search-card-stack")
    data class SearchCardStack(val type: CardType) : GameClientMessage()
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
