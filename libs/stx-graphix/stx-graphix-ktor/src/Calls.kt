package com.strange.graphix.ktor

import com.strange.graphix.Graphix
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/**
 * The application's Graphix engine, as [GraphQL] installed it.
 *
 * Throws naming the plugin if `install(GraphQL)` never ran — a missing install should fail
 * the first request loudly, not as a null three layers down.
 */
val Application.graphix: Graphix
    get() =
        attributes.getOrNull(GraphixKey)
            ?: error("the GraphQL plugin is not installed — call install(GraphQL) { … } first")

/** The same engine, from a route. Shared: this is not per-request. */
val ApplicationCall.graphix: Graphix get() = application.graphix
