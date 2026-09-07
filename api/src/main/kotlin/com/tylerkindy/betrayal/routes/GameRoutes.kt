package com.tylerkindy.betrayal.routes

import com.tylerkindy.betrayal.Game
import com.tylerkindy.betrayal.db.Games
import com.tylerkindy.betrayal.db.Players
import com.tylerkindy.betrayal.gameUpdateManager
import com.tylerkindy.betrayal.routes.GameClientMessage.NameMessage
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
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
                val id = call.parameters["gameId"]!!

                val (name) = parseMessage<GameClientMessage>(incoming.receive())
                        as? NameMessage
                    ?: return@webSocket close(
                        CloseReason(
                            CloseReason.Codes.VIOLATED_POLICY,
                            "Expected name message"
                        )
                    )

                transaction {
                    Players.select { (Players.gameId eq id) and (Players.name eq name) }
                        .firstOrNull()
                }
                    ?: return@webSocket close(
                        CloseReason(
                            CloseReason.Codes.VIOLATED_POLICY,
                            "Player $name is not part of this game"
                        )
                    )

                gameUpdateManager.getUpdates(id).collect { update ->
                    send(Json.encodeToString(update))
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
}
