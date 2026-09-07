package com.tylerkindy.betrayal

import com.tylerkindy.betrayal.routes.gameRoutes
import com.tylerkindy.betrayal.routes.lobbyRoutes
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.slf4j.event.Level
import java.time.Duration

fun main() {
    Database.connect(
        HikariDataSource().apply {
            jdbcUrl = System.getenv("JDBC_DATABASE_URL")
        }
    )
    val port = System.getenv("PORT")?.toInt() ?: 8080

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            json(
                Json {
                    encodeDefaults = true
                    isLenient = true
                }
            )
        }
        install(CORS) {
            allowNonSimpleContentTypes = true
            allowHost("localhost:3000")
            allowHost(
                "herokuapp.com",
                schemes = listOf("https"),
                subDomains = listOf("betrayal-ui")
            )
            allowHost(
                "tylerkindy.com",
                schemes = listOf("http", "https"),
                subDomains = listOf("betrayal")
            )
        }
        install(CallLogging) {
            level = Level.INFO
        }
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(30)
        }
        install(StatusPages) {
            exception<IllegalArgumentException> { call, cause ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to cause.message)
                )
                throw cause
            }
        }

        routing {
            lobbyRoutes()
            gameRoutes()
        }
    }.start(wait = true)
}
