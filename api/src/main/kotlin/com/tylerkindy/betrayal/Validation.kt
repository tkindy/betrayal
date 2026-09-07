package com.tylerkindy.betrayal

import com.tylerkindy.betrayal.routes.LobbyState

fun validatePlayerName(name: String): String? {
    return if (name.length !in 1..20) {
        "Name must be between 1 and 20 characters"
    } else null
}

fun validatePlayerName(name: String, state: LobbyState): String? {
    return validatePlayerName(name)
        ?: if (name in state.players.map { it.name }) {
            "A player in the lobby is already using the name $name"
        } else null
}
