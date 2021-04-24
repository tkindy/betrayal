package com.tylerkindy.betrayal.routes

import com.tylerkindy.betrayal.Game
import com.tylerkindy.betrayal.GameRequest
import com.tylerkindy.betrayal.db.*
import com.tylerkindy.betrayal.getUpdates
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
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
                        ?.toGame()
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
                getUpdates(gameId).collect { update ->
                    send(Frame.Text(Json.encodeToString(update)))
                }
            }
        }

        post {
            val gameRequest = call.receiveOrNull<GameRequest>()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "'name' and 'numPlayers' are required")

            if (gameRequest.name.length > 32) {
                return@post call.respond(HttpStatusCode.BadRequest, "Name can only be up to 32 characters")
            }
            if (!(3..6).contains(gameRequest.numPlayers)) {
                return@post call.respond(HttpStatusCode.BadRequest, "Must have between 3 and 6 players")
            }

            val gameId = buildGameId()

            transaction {
                Games.insert {
                    it[id] = gameId
                    it[name] = gameRequest.name
                }
            }

            insertStartingRooms(gameId)
            insertStartingPlayers(gameId, gameRequest.numPlayers)
            createRoomStack(gameId)
            createCardStacks(gameId)

            call.respond(Game(id = gameId, name = gameRequest.name))
        }
    }
}

private val gameIdCharacters = ('A'..'Z').toList()
private fun buildGameId(): String {
    return (0 until 6)
        .map { gameIdCharacters.random() }
        .fold(StringBuilder()) { builder, char -> builder.append(char) }
        .toString()
}
