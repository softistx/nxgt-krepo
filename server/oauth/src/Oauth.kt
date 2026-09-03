package com.softistx.oauth

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

/** Installs GraphQL at `/graphql`, and the Apollo Sandbox at `/sandbox`. */
fun Application.oauth() {
    configureDependencyInjection()
    configureDatabase()
    configureSockets()
    configureGraphQL()
    configureValidation()
    configureTemplating()
    configureSerialization()
    configureHTTP()
    configureSecurity()
    configureRouting()
}
