package com.tylerkindy.betrayal.routes

import com.tylerkindy.betrayal.GridLoc
import com.tylerkindy.betrayal.db.addMonster
import com.tylerkindy.betrayal.db.getMonsters
import com.tylerkindy.betrayal.db.moveMonster
import com.tylerkindy.betrayal.gameUpdateManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

val monsterRoutes: Route.() -> Unit = {
    route("monsters") {
        get {
            val gameId = call.parameters["gameId"]!!
            call.respond(getMonsters(gameId))
        }

        post {
            val gameId = call.parameters["gameId"]!!
            call.respond(addMonster(gameId))
            gameUpdateManager.sendUpdate(gameId)
        }

        route("{monsterId}") {
            post("move") {
                val gameId = call.parameters["gameId"]!!
                val monsterId = call.parameters["monsterId"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid monster ID")
                val loc = call.receiveOrNull<GridLoc>()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing gridX or gridY")

                call.respond(moveMonster(gameId, monsterId, loc))
                gameUpdateManager.sendUpdate(gameId)
            }
        }
    }
}
