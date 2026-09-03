package com.softistx.oauth.plugins

import com.softistx.mongo.ktor.MongoDB
import com.softistx.oauth.config
import io.ktor.server.application.*

fun Application.configureDatabase() {
    install(MongoDB) {
        uri = config.property("stx.mongo.uri").getString()
        database = config.property("stx.mongo.database").getString()
    }
}
