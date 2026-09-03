package com.softistx.oauth.plugins

import com.softistx.graphix.ktor.GraphQL
import com.softistx.oauth.controllers.PermissionController
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

fun Application.configureGraphQL() {
    val permissions by inject<PermissionController>()
    install(GraphQL) {
        sandbox = true
        builtInScalars = true
        schema { resolvers(permissions) }
    }
}
