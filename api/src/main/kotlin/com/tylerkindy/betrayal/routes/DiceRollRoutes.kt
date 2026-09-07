package com.tylerkindy.betrayal.routes

import com.tylerkindy.betrayal.DiceRollType
import com.tylerkindy.betrayal.db.getLatestRoll
import com.tylerkindy.betrayal.db.rollDice
import com.tylerkindy.betrayal.gameUpdateManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

val diceRollRoutes: Route.() -> Unit = {
    route("dice") {
        get {
            val gameId = call.parameters["gameId"]!!

            val latestRoll = getLatestRoll(gameId)
                ?: return@get call.respond(HttpStatusCode.NotFound, "")
            call.respond(latestRoll)
        }

        post {
            val gameId = call.parameters["gameId"]!!
            val body = call.receiveOrNull<RollDiceBody>()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing numDice")

            call.respond(rollDice(gameId, body.numDice, body.type))
            gameUpdateManager.sendUpdate(gameId)
        }
    }
}

@Serializable
data class RollDiceBody(
    val numDice: Int,
    val type: DiceRollType = DiceRollType.AD_HOC
)
