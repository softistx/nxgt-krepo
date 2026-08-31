package com.strange.graphix.ktor

import com.strange.common.serialization.lenientJson
import com.strange.graphix.GraphQLEngineCustomizer
import com.strange.graphix.Graphix
import com.strange.graphix.GraphixBuilder
import com.strange.graphix.GraphixCustomizer
import com.strange.graphix.engine
import com.strange.graphix.http.SubscriptionProtocol
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import graphql.GraphQL as GraphQLEngine

/**
 * One GraphQL engine for the application, served at [GraphQLConfiguration.path].
 *
 * ```kotlin
 * install(GraphQL) {
 *     schema {
 *         query(ProductQueries(store))
 *         mutation(ProductMutations(store))
 *         subscription(ProductSubscriptions(store))
 *         type(ProductFields(reviews))
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
                ?: Graphix(pluginConfig.json) {
                    schemaLocations(pluginConfig.schemaLocations)
                    schemaFileExtensions(pluginConfig.schemaFileExtensions)
                    introspection(pluginConfig.introspection)
                    val block = pluginConfig.schemaBlock ?: error("install(GraphQL) needs schema { … } or instance")
                    block()
                    pluginConfig.customizeBlock?.invoke(this)
                    if (pluginConfig.fromDi) application.applyGraphixDi(this)
                    pluginConfig.engineBlock?.let { engine(it) }
                }
        application.attributes.put(GraphixKey, engine)
        val path = pluginConfig.path
        val json = pluginConfig.json
        val subscriptions = pluginConfig.subscriptions
        if (subscriptions == SubscriptionProtocol.GraphqlWs && application.pluginOrNull(WebSockets) == null) {
            application.install(WebSockets)
        }
        application.routing { graphqlRoute(path, engine, json, subscriptions) }
        if (pluginConfig.injectable) application.provideGraphix()
    }

/** What [GraphQL] installs with. Either [schema] or [instance] must be set. */
class GraphQLConfiguration {
    /** HTTP path for POST and GET. Default `/graphql`. */
    var path: String = "/graphql"

    /**
     * How subscriptions are served. [SubscriptionProtocol.Sse] (default) is
     * `text/event-stream` on POST. [SubscriptionProtocol.GraphqlWs] is a WebSocket
     * on the same path; HTTP POST of a subscription is then 400.
     */
    var subscriptions: SubscriptionProtocol = SubscriptionProtocol.Sse

    /**
     * Whether `__schema` and `__type` answer. On by default — GraphiQL and Apollo Sandbox need
     * them. Ignored when [instance] is set: that engine already decided.
     */
    var introspection: Boolean = true

    /**
     * An engine built elsewhere. When set, [schema] is ignored. Whoever created it owns it —
     * graphql-java has no socket to close, so this plugin never calls `close`.
     */
    var instance: Graphix? = null

    /** How the HTTP envelope and GraphQL arguments are decoded. */
    var json: Json = lenientJson

    /**
     * Registers the engine with Ktor DI. Off by default: `ktor-server-di` is compile-only.
     */
    var injectable: Boolean = false

    /**
     * Directories of `.graphqls` / `.gqls` files. Default `classpath:graphql/`, the same
     * place Spring GraphQL looks. Several files merge. An empty scan keeps the annotated schema.
     */
    var schemaLocations: List<String> = listOf("classpath:graphql/")

    /** File suffixes under [schemaLocations]. Default `.graphqls` and `.gqls`. */
    var schemaFileExtensions: List<String> = listOf(".graphqls", ".gqls")

    /**
     * Pull [GraphixCustomizer], scalars and field directives from Ktor DI — the same
     * beans Spring collects. Off by default: `ktor-server-di` is compile-only.
     */
    var fromDi: Boolean = false

    internal var schemaBlock: (GraphixBuilder.() -> Unit)? = null
    internal var customizeBlock: (GraphixBuilder.() -> Unit)? = null
    internal var engineBlock: GraphQLEngineCustomizer? = null

    /**
     * Builds the engine at install. Query/mutation/subscription instances passed here are
     * kept for the life of the application — put stores and Spring-like services on those
     * instances.
     */
    fun schema(block: GraphixBuilder.() -> Unit) {
        schemaBlock = block
    }

    /** Extra [GraphixBuilder] configuration after [schema], same role as a Spring [GraphixCustomizer] bean. */
    fun customize(block: GraphixBuilder.() -> Unit) {
        customizeBlock = block
    }

    /** graphql-java [GraphQLEngine.Builder] after the schema is built. */
    fun engine(block: GraphQLEngine.Builder.() -> Unit) {
        engineBlock = GraphQLEngineCustomizer(block)
    }
}

/** Application attribute the [GraphQL] plugin writes. */
internal val GraphixKey = AttributeKey<Graphix>("com.strange.graphix.Graphix")
