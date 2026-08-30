package com.strange.example.graphix.shop

import com.strange.graphix.ktor.GraphQL
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * A shop catalogue over GraphQL — the smallest thing that shows `stx-graphix-ktor` end to end.
 *
 * ```
 * ./kotlin run -m graphix-shop
 * ```
 *
 * Then `POST /graphql` with `{ "query": "{ products { name price } }" }`.
 */
fun main() {
    embeddedServer(Netty, port = 8080, module = Application::shop).start(wait = true)
}

/** Installs GraphQL at `/graphql` over an in-memory [Catalog]. */
fun Application.shop() {
    val catalog = Catalog()
    install(GraphQL) {
        schema {
            query(catalog)
            mutation(catalog)
        }
    }
}
