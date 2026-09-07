package com.tylerkindy.betrayal.routes

import com.tylerkindy.betrayal.Card
import com.tylerkindy.betrayal.db.*
import com.tylerkindy.betrayal.gameUpdateManager
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val cardRoutes: Route.() -> Unit = {
    route("cards") {
        route("items") {
            post("draw") {
                val gameId = call.parameters["gameId"]!!
                call.respondWithCard(drawItem(gameId))
                gameUpdateManager.sendUpdate(gameId)
            }
        }

        route("events") {
            post("draw") {
                val gameId = call.parameters["gameId"]!!
                call.respondWithCard(drawEvent(gameId))
                gameUpdateManager.sendUpdate(gameId)
            }
        }

        route("omens") {
            post("draw") {
                val gameId = call.parameters["gameId"]!!
                call.respondWithCard(drawOmen(gameId))
                gameUpdateManager.sendUpdate(gameId)
            }
        }

        route("drawn") {
            get {
                val gameId = call.parameters["gameId"]!!
                val card = getDrawnCard(gameId)

                if (card == null) {
                    call.respond(HttpStatusCode.NotFound, "")
                    return@get
                }

                call.respondWithCard(card)
            }

            post("discard") {
                val gameId = call.parameters["gameId"]!!
                discardDrawnCard(gameId)
                call.respond(HttpStatusCode.OK, "")
                gameUpdateManager.sendUpdate(gameId)
            }

            post("giveToPlayer") {
                val gameId = call.parameters["gameId"]!!
                val playerId = call.receiveOrNull<GiveToPlayerBody>()?.playerId
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing playerId")

                call.respond(giveDrawnCardToPlayer(gameId, playerId))
                gameUpdateManager.sendUpdate(gameId)
            }
        }
    }
}

// Workaround because of ktor top-level serialization bug
// https://github.com/ktorio/ktor/issues/1783#issuecomment-727645523
suspend inline fun ApplicationCall.respondWithCard(card: Card) {
    response.pipeline.execute(
        this,
        TextContent(
            text = Json.encodeToString(card),
            ContentType.Application.Json,
            HttpStatusCode.OK
        )
    )
}

@Serializable
data class GiveToPlayerBody(
    val playerId: Int
)
