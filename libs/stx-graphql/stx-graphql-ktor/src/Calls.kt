package com.strange.graphql.ktor

import com.strange.graphql.GraphQl
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The application's GraphQL engine, as [GraphQL] installed it. */
val Application.graphQl: GraphQl
    get() =
        attributes.getOrNull(GraphQlKey)
            ?: error("the GraphQL plugin is not installed — call install(GraphQL) { … } first")

/** The same engine, from a route. */
val ApplicationCall.graphQl: GraphQl get() = application.graphQl
