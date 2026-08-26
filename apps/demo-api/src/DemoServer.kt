package com.strange.demo.api

import com.strange.demo.api.routes.DemoData
import com.strange.demo.api.routes.categoryRoutes
import com.strange.demo.api.routes.failureRoutes
import com.strange.demo.api.routes.notificationRoutes
import com.strange.demo.api.routes.sessionRoutes
import com.strange.demo.api.routes.tagRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking

/** Installs the categories, tags, notifications, session and failures slice of `openapi.yaml`. */
public fun Application.demoApi() {
    val data = DemoData()
    install(ContentNegotiation) { json() }
    routing {
        categoryRoutes(data)
        tagRoutes(data)
        notificationRoutes(data)
        sessionRoutes()
        failureRoutes()
    }
}

/**
 * A running demo server.
 *
 * The Ktor engine stays behind this handle so callers — the end-to-end test in particular —
 * need no Ktor server dependency of their own.
 */
public class DemoServer internal constructor(
    /** The port actually bound, which is what matters when starting on port 0. */
    public val port: Int,
    private val shutdown: () -> Unit,
) : AutoCloseable {
    /** Base URL to hand to Ktorfit, trailing slash included so relative paths resolve. */
    public val baseUrl: String get() = "http://127.0.0.1:$port/"

    override fun close(): Unit = shutdown()
}

/** Starts the demo server. Port 0 binds an ephemeral port; read it back from [DemoServer.port]. */
public fun startDemoServer(port: Int = 0): DemoServer {
    val server = embeddedServer(Netty, port = port, module = Application::demoApi)
    server.start(wait = false)
    val bound =
        runBlocking {
            server.engine
                .resolvedConnectors()
                .first()
                .port
        }
    return DemoServer(bound) { server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000) }
}

public fun main() {
    val server = startDemoServer(port = 8080)
    println("demo-api listening on ${server.baseUrl}")
    Thread.currentThread().join()
}
