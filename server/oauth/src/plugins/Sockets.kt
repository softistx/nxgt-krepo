package com.softistx.oauth.plugins

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration

fun Application.configureSockets() {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
        pingPeriod = Duration.parse("15s")
        timeout = Duration.parse("15s")
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}
