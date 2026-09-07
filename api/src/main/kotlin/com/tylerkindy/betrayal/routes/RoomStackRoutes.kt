package com.tylerkindy.betrayal.routes

import com.tylerkindy.betrayal.GridLoc
import com.tylerkindy.betrayal.RoomStackResponse
import com.tylerkindy.betrayal.db.*
import com.tylerkindy.betrayal.gameUpdateManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

val roomStackRoutes: Route.() -> Unit = {
    route("roomStack") {
        get {
            val gameId = call.parameters["gameId"]!!
            call.respond(getRoomStackState(gameId))
        }

        post("skip") {
            val gameId = call.parameters["gameId"]!!
            call.respond(
                RoomStackResponse(
                    nextRoom = advanceRoomStack(gameId)
                )
            )
            gameUpdateManager.sendUpdate(gameId)
        }

        post("flip") {
            val gameId = call.parameters["gameId"]!!
            call.respond(
                RoomStackResponse(
                    flippedRoom = flipRoom(gameId)
                )
            )
            gameUpdateManager.sendUpdate(gameId)
        }

        post("rotate") {
            val gameId = call.parameters["gameId"]!!
            rotateFlipped(gameId)
            call.respond(getRoomStackState(gameId))
            gameUpdateManager.sendUpdate(gameId)
        }

        post("place") {
            val gameId = call.parameters["gameId"]!!
            val loc = call.receiveOrNull<GridLoc>()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Must pass grid location for room")
            call.respond(
                placeRoom(gameId, loc)
            )
            gameUpdateManager.sendUpdate(gameId)
        }
    }
}
