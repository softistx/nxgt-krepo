package com.softistx.oauth

import com.softistx.graphix.ktor.GraphQL
import com.softistx.oauth.controllers.PermissionController
import com.softistx.oauth.plugins.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

val config by lazy {
    ApplicationConfig("application.yaml")
}

fun main() {
    embeddedServer(Netty, port = config.port, host = config.host, module = Application::oauth).start(wait = true)
}

/** Installs GraphQL at `/graphql`, and the Apollo Sandbox at `/sandbox`, over an in-memory [Catalog]. */
fun Application.oauth() {
    val permission = PermissionController()
    configureDependencyInjection()
    configureValidation()
    configureSockets()
    configureTemplating()
    configureSerialization()
    configureHTTP()
    configureSecurity()
    configureRouting()
    install(GraphQL) {
        sandbox = true
        schema {
            query(permission)
            mutation(permission)
            type(permission)
        }
    }
}
