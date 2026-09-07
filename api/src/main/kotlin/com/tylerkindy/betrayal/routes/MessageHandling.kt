package com.tylerkindy.betrayal.routes

import io.ktor.websocket.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

inline fun <reified T> parseMessage(frame: Frame): T? {
    val textFrame = frame as? Frame.Text
        ?: return null
    return Json.decodeFromString(textFrame.readText())
}
