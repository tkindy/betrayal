package com.tylerkindy.betrayal

import com.tylerkindy.betrayal.db.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable
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

@Serializable
data class GameUpdate(
    val rooms: List<Room>,
    val players: List<Player>,
    val roomStack: RoomStackResponse,
    val drawnCard: Card?,
    val latestRoll: DiceRoll?,
    val monsters: List<Monster>
)

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
