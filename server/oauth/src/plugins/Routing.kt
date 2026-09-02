package com.softistx.oauth.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    install(AutoHeadResponse)
    routing {
        staticResources("/main/resources/static", "main/resources/static")
        get("/") {
        }
    }
}
