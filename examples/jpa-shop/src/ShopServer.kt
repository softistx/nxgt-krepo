package com.strange.example.shop

import com.strange.example.shop.domain.Product
import com.strange.example.shop.routes.productRoutes
import com.strange.jpa.JpaConfig
import com.strange.jpa.JpaNotFoundException
import com.strange.jpa.SchemaMode
import com.strange.ktor.jpa.JpaConnection
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing

/**
 * A shop catalogue over Postgres, and the smallest thing that shows the whole layer.
 *
 * ```
 * POSTGRES_URI=postgresql://localhost:5432/shop POSTGRES_USER=… POSTGRES_PASSWORD=… ./kotlin run -m jpa-shop
 * ```
 *
 * What it demonstrates, in the order the request goes:
 * a route reads in `session { }` and writes in `transaction { }`; a `ProductService` maps the
 * request and refuses a write with no transaction; a `ProductRepository` speaks the typed DSL; and
 * `Product` extends `AuditedEntity`, so who and when are recorded without a route saying so.
 */
fun main() {
    embeddedServer(Netty, port = 8080, module = Application::shop).start(wait = true)
}

fun Application.shop() {
    install(ContentNegotiation) { json() }

    install(JpaConnection) {
        config =
            JpaConfig(
                uri = System.getenv("POSTGRES_URI") ?: "postgresql://localhost:5432/shop",
                username = System.getenv("POSTGRES_USER"),
                password = System.getenv("POSTGRES_PASSWORD"),
                // An example creates its own tables. A deployment would use VALIDATE and a migration
                // tool, which also turns a wrong password into a failed startup rather than a failed
                // first request.
                schemaMode = SchemaMode.CREATE_DROP,
            )
        entities(Product::class)
    }

    install(StatusPages) {
        exception<JpaNotFoundException> { call, failure ->
            call.respond(HttpStatusCode.NotFound, failure.message ?: "not found")
        }
    }

    routing { productRoutes() }
}
