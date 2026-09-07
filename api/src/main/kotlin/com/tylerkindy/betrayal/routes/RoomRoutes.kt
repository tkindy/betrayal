package com.tylerkindy.betrayal.routes

import com.tylerkindy.betrayal.GridLoc
import com.tylerkindy.betrayal.db.getRooms
import com.tylerkindy.betrayal.db.moveRoom
import com.tylerkindy.betrayal.db.returnRoomToStack
import com.tylerkindy.betrayal.db.rotateRoom
import com.tylerkindy.betrayal.gameUpdateManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

val roomRoutes: Route.() -> Unit = {
    route("rooms") {
        get {
            val gameId = call.parameters["gameId"]!!
            return@get call.respond(getRooms(gameId))
        }

        route("{roomId}") {
            post("move") {
                val gameId = call.parameters["gameId"]!!
                val roomId = call.parameters["roomId"]?.toInt()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid room ID")
                val loc = call.receiveOrNull<GridLoc>()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing gridX or gridY")

                moveRoom(gameId, roomId, loc)
                call.respond(HttpStatusCode.OK, "")
                gameUpdateManager.sendUpdate(gameId)
            }

            post("rotate") {
                val gameId = call.parameters["gameId"]!!
                val roomId = call.parameters["roomId"]?.toInt()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid room ID")

                rotateRoom(gameId, roomId)
                call.respond(HttpStatusCode.OK, "")
                gameUpdateManager.sendUpdate(gameId)
            }

            post("return") {
                val gameId = call.parameters["gameId"]!!
                val roomId = call.parameters["roomId"]?.toInt()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid room ID")

                returnRoomToStack(gameId, roomId)
                call.respond(HttpStatusCode.OK, "")
                gameUpdateManager.sendUpdate(gameId)
            }
        }
    }
}
