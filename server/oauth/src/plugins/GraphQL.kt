package com.softistx.oauth.plugins

import com.softistx.graphix.koin.fromKoin
import com.softistx.graphix.ktor.GraphQL
import io.ktor.server.application.*
import org.koin.ktor.ext.getKoin

fun Application.configureGraphQL() {
    install(GraphQL) {
        sandbox = true
        builtInScalars = true
        schema {
            fromKoin(getKoin())
        }
    }
}
