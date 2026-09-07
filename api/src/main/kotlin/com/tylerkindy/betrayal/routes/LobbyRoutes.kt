package com.tylerkindy.betrayal.routes

import com.tylerkindy.betrayal.Lobby
import com.tylerkindy.betrayal.LobbyRequest
import com.tylerkindy.betrayal.db.*
import com.tylerkindy.betrayal.gameUpdateManager
import com.tylerkindy.betrayal.routes.LobbyServerMessage.PlayersMessage
import com.tylerkindy.betrayal.validatePlayerName
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

data class LobbyState(val players: List<PlayerState> = listOf())
data class PlayerState(val name: String, val session: WebSocketSession)

val lobbyStates = ConcurrentHashMap<String, LobbyState>()

val lobbyRoutes: Routing.() -> Unit = {
    route("lobbies") {
        post {
            val lobbyRequest = call.receiveOrNull<LobbyRequest>()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "'hostName' is required")

            val (hostName) = lobbyRequest
            validatePlayerName(hostName)?.let { error ->
                return@post call.respond(HttpStatusCode.BadRequest, error)
            }

            val lobbyId = buildLobbyId()
            lobbyStates[lobbyId] = LobbyState()
            call.respond(Lobby(id = lobbyId))
        }

        route("{lobbyId}") {
            webSocket {
                val lobbyId = call.parameters["lobbyId"]!!

                val (name) = parseMessage<NameMessage>(incoming.receive())
                    ?: return@webSocket close(
                        CloseReason(
                            CloseReason.Codes.VIOLATED_POLICY,
                            "Expected name message"
                        )
                    )
                val curState = lobbyStates[lobbyId]
                if (curState == null) {
                    getPlayerByName(lobbyId, name)?.let {
                        send(Json.encodeToString(LobbyServerMessage.JoinGame as LobbyServerMessage))
                        return@webSocket close(CloseReason(CloseReason.Codes.NORMAL, "Game has already started"))
                    }
                    return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unknown lobby"))
                }

                validatePlayerName(name, curState)?.let { error ->
                    return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, error))
                }

                val player = PlayerState(name, this)
                val newState = lobbyStates.computeIfPresent(lobbyId) { _, state ->
                    state.copy(
                        players = state.players + player
                    )
                }!!

                val playerNamesMessage =
                    Json.encodeToString(
                        PlayersMessage(players = newState.players.map { it.name }) as LobbyServerMessage
                    )
                newState.players.forEach {
                    it.session.send(playerNamesMessage)
                }

                for (frame in incoming) {
                    val message =
                        parseMessage<LobbyClientMessage>(frame) ?: continue

                    when (message) {
                        is LobbyClientMessage.StartGame -> {
                            if (startGame(lobbyId)) {
                                lobbyStates -= lobbyId
                                return@webSocket close(
                                    CloseReason(
                                        CloseReason.Codes.NORMAL,
                                        "Game started"
                                    )
                                )
                            }
                        }
                    }
                }

                removePlayerThatLeft(lobbyId, player)
            }
        }

    }
}

private val lobbyIdCharacters = ('A'..'Z').toList()
private fun buildLobbyId(): String {
    return (0 until 6)
        .map { lobbyIdCharacters.random() }
        .fold(StringBuilder()) { builder, char -> builder.append(char) }
        .toString()
}

@Serializable
data class NameMessage(val name: String)

@Serializable
sealed class LobbyServerMessage {
    @Serializable
    @SerialName("players")
    data class PlayersMessage(val players: List<String>) : LobbyServerMessage()

    @Serializable
    @SerialName("join")
    object JoinGame : LobbyServerMessage()

    @Serializable
    @SerialName("start-game/error")
    data class StartGameError(val reason: String) : LobbyServerMessage()
}

@Serializable
sealed class LobbyClientMessage {
    @Serializable
    @SerialName("start-game")
    object StartGame : LobbyClientMessage()
}

private suspend fun WebSocketSession.startGame(lobbyId: String): Boolean {
    suspend fun error(reason: String): Boolean {
        send(
            Json.encodeToString(
                LobbyServerMessage.StartGameError(reason) as LobbyServerMessage
            )
        )
        return false
    }

    val state = lobbyStates[lobbyId]!!
    val gameName = "name"

    if (state.players.size > 6) {
        return error("Must have fewer than 6 players")
    }

    transaction {
        Games.insert {
            it[id] = lobbyId
            it[name] = gameName
        }
    }

    insertStartingRooms(lobbyId)
    insertStartingPlayers(lobbyId, state.players)
    createRoomStack(lobbyId)
    createCardStacks(lobbyId)

    gameUpdateManager.sendUpdate(lobbyId)

    val joinGameMessage = Json.encodeToString(
        LobbyServerMessage.JoinGame as LobbyServerMessage
    )
    state.players.forEach { player ->
        player.session.send(joinGameMessage)
    }

    return true
}

private fun insertStartingPlayers(gameId: String, players: List<PlayerState>) {
    val characters = getRandomCharacters(players.size)
    transaction {
        players.shuffled()
            .zip(characters.shuffled())
            .forEach { (player, character) ->
                Players.insert {
                    it[this.gameId] = gameId
                    it[name] = player.name
                    it[characterId] = character.id
                    it[gridX] = entranceHallLoc.gridX
                    it[gridY] = entranceHallLoc.gridY
                    it[speedIndex] = character.speed.startingIndex.toShort()
                    it[mightIndex] = character.might.startingIndex.toShort()
                    it[sanityIndex] = character.sanity.startingIndex.toShort()
                    it[knowledgeIndex] = character.knowledge.startingIndex.toShort()
                }
            }
    }
}

private suspend fun removePlayerThatLeft(lobbyId: String, player: PlayerState) {
    val removedState = lobbyStates.computeIfPresent(lobbyId) { _, state ->
        state.copy(
            players = state.players - player
        )
    }!!

    val newPlayerNamesMessage =
        Json.encodeToString(
            PlayersMessage(players = removedState.players.map { it.name }) as LobbyServerMessage
        )

    removedState.players.forEach {
        it.session.send(newPlayerNamesMessage)
    }
}
