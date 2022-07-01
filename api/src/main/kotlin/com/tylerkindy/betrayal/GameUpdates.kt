package com.tylerkindy.betrayal

import com.tylerkindy.betrayal.db.*
import com.tylerkindy.betrayal.routes.GameServerMessage.GameUpdate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap

val gameUpdateManager = UpdateManager { gameId ->
    GameUpdate(
        rooms = getRooms(gameId),
        players = getPlayers(gameId),
        roomStack = getRoomStackState(gameId),
        drawnCard = getDrawnCard(gameId),
        latestRoll = getLatestRoll(gameId),
        monsters = getMonsters(gameId)
    )
}

class UpdateManager<T>(private val buildUpdate: (String) -> T) {
    private val flows = ConcurrentHashMap<String, MutableSharedFlow<T>>()

    fun getUpdates(id: String): SharedFlow<T> =
        getOrCreateFlow(id)

    suspend fun sendUpdate(id: String) {
        getOrCreateFlow(id).emit(buildUpdate(id))
    }

    private fun getOrCreateFlow(id: String) =
        flows.computeIfAbsent(id) { MutableSharedFlow(replay = 1) }
}
