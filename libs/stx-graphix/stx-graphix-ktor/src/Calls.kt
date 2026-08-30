package com.strange.graphix.ktor

import com.strange.graphix.Graphix
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The application's GraphQL engine, as [GraphQL] installed it. */
val Application.graphix: Graphix
    get() =
        attributes.getOrNull(GraphixKey)
            ?: error("the GraphQL plugin is not installed — call install(GraphQL) { … } first")

/** The same engine, from a route. */
val ApplicationCall.graphix: Graphix get() = application.graphix
