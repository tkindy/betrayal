package com.tylerkindy.betrayal.db

import com.tylerkindy.betrayal.Game
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toGame(): Game = Game(id = this[Games.id], name = this[Games.name])
