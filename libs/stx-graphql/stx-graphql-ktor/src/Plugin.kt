package com.strange.graphql.ktor

import com.strange.common.serialization.lenientJson
import com.strange.graphql.GraphQl
import com.strange.graphql.GraphQlBuilder
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json

/**
 * One GraphQL engine for the application, served at [GraphQLConfiguration.path].
 *
 * ```kotlin
 * install(GraphQL) {
 *     schema {
 *         query(ProductQueries(store))
 *         mutation(ProductMutations(store))
 *     }
 * }
 * ```
 *
 * [instance] adopts an engine built elsewhere and is not closed — there is nothing to close on
 * graphql-java. The plugin only registers routes.
 */
val GraphQL =
    createApplicationPlugin(name = "GraphQL", createConfiguration = ::GraphQLConfiguration) {
        val engine =
            pluginConfig.instance
                ?: GraphQl(pluginConfig.json, pluginConfig.schemaBlock ?: error("install(GraphQL) needs schema { … } or instance"))
        application.attributes.put(GraphQlKey, engine)
        val path = pluginConfig.path
        val json = pluginConfig.json
        application.routing { graphqlRoute(path, engine, json) }
        if (pluginConfig.injectable) application.provideGraphQl()
    }

class GraphQLConfiguration {
    /** HTTP path. Default `/graphql`. */
    var path: String = "/graphql"

    /**
     * An engine built elsewhere. When set, [schema] is ignored. Whoever created it owns it —
     * graphql-java has no socket to close, so this plugin never calls `close`.
     */
    var instance: GraphQl? = null

    var json: Json = lenientJson

    /**
     * Registers the engine with Ktor DI. Off by default: `ktor-server-di` is compile-only.
     */
    var injectable: Boolean = false

    internal var schemaBlock: (GraphQlBuilder.() -> Unit)? = null

    fun schema(block: GraphQlBuilder.() -> Unit) {
        schemaBlock = block
    }
}

internal val GraphQlKey = AttributeKey<GraphQl>("com.strange.graphql.GraphQl")
