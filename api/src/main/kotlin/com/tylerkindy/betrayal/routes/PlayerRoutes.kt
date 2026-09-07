package com.tylerkindy.betrayal.routes

import com.tylerkindy.betrayal.GridLoc
import com.tylerkindy.betrayal.TraitName
import com.tylerkindy.betrayal.db.*
import com.tylerkindy.betrayal.gameUpdateManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

val playerRoutes: Route.() -> Unit = {
    route("players") {
        get {
            val gameId = call.parameters["gameId"]!!
            return@get call.respond(getPlayers(gameId))
        }

        route("{playerId}") {
            post("move") {
                val gameId = call.parameters["gameId"]!!
                val playerId = call.parameters["playerId"]!!.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid player ID")
                val loc = call.receiveOrNull<GridLoc>()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Must specify new location")

                movePlayer(gameId, playerId, loc)
                call.respond(HttpStatusCode.OK)
                gameUpdateManager.sendUpdate(gameId)
            }

            post("setTrait") {
                val gameId = call.parameters["gameId"]!!
                val playerId = call.parameters["playerId"]!!.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid player ID")
                val body = call.receiveOrNull<SetTraitBody>()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing trait or index")

                setTrait(gameId, playerId, body.trait, body.index)
                call.respond(getPlayer(gameId, playerId))
                gameUpdateManager.sendUpdate(gameId)
            }

            route("cards/{cardId}") {
                post("discard") {
                    val gameId = call.parameters["gameId"]!!
                    val playerId = call.parameters["playerId"]!!.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid player ID")
                    val cardId = call.parameters["cardId"]!!.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid card ID")

                    discardHeldCard(gameId, playerId, cardId)
                    call.respond(getPlayer(gameId, playerId))
                    gameUpdateManager.sendUpdate(gameId)
                }
                post("giveToPlayer") {
                    val gameId = call.parameters["gameId"]!!
                    val fromPlayerId = call.parameters["playerId"]!!.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid player ID")
                    val cardId = call.parameters["cardId"]!!.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid card ID")
                    val toPlayerId = call.receiveOrNull<GiveHeldCardToPlayerBody>()?.toPlayerId
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing toPlayerId")

                    giveHeldCardToPlayer(gameId, fromPlayerId, cardId, toPlayerId)
                    call.respond(getPlayers(gameId))
                    gameUpdateManager.sendUpdate(gameId)
                }
            }
        }
    }
}

@Serializable
data class GiveHeldCardToPlayerBody(val toPlayerId: Int)

@Serializable
data class SetTraitBody(val trait: TraitName, val index: Int)
