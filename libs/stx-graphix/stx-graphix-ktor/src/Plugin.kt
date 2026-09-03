package com.softistx.graphix.ktor

import com.softistx.common.serialization.lenientJson
import com.softistx.graphix.*
import com.softistx.graphix.error.GraphixErrorSpec
import com.softistx.graphix.error.GraphixExceptionHandler
import com.softistx.graphix.error.errors
import com.softistx.graphix.error.exceptionHandler
import com.softistx.graphix.http.SubscriptionProtocol
import com.softistx.graphix.http.apolloSandboxPage
import com.softistx.graphix.intercept.GraphixChain
import com.softistx.graphix.intercept.GraphixInterceptor
import com.softistx.graphix.intercept.intercept
import com.softistx.graphix.schema.contextParameter
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import graphql.GraphQL as GraphQLEngine

/**
 * One GraphQL engine for the application, served at [GraphQLConfiguration.path].
 *
 * ```kotlin
 * install(GraphQL) {
 *     schema {
 *         resolvers(
 *             ProductQueries(store),
 *             ProductMutations(store),
 *             ProductSubscriptions(store),
 *             ProductFields(reviews),
 *         )
 *     }
 * }
 * ```
 *
 * [instance] adopts an engine built elsewhere and is not closed — there is nothing to close on
 * graphql-java. The plugin only registers routes.
 *
 * **Every operation carries its [ApplicationCall]**, over POST, over SSE and over graphql-ws alike,
 * so a resolver takes one as a parameter and an interceptor reads it as [GraphixChain.call]. See
 * [GraphQLConfiguration.intercept].
 *
 * **Installing it registers the engine with Ktor's DI**, so a class the container builds takes a
 * [Graphix] in its constructor. See [provideGraphix].
 */
val GraphQL =
    createApplicationPlugin(name = "GraphQL", createConfiguration = ::GraphQLConfiguration) {
        val adopted = pluginConfig.instance
        // A configuration field the plugin quietly ignores is worse than one it refuses: nothing
        // fails, and the interceptor in the file is not the interceptor in force. Interceptors live
        // on the engine, so an adopted one carries whatever was registered where it was built.
        if (adopted != null && pluginConfig.interceptors.isNotEmpty()) {
            error("install(GraphQL) got both instance and intercept { } — register interceptors where that engine is built")
        }
        if (adopted != null && (pluginConfig.errorBlocks.isNotEmpty() || pluginConfig.exceptionHandlers.isNotEmpty())) {
            error("install(GraphQL) got both instance and errors { } — register handlers where that engine is built")
        }
        val engine =
            adopted
                ?: Graphix(pluginConfig.json) {
                    schemaLocations(pluginConfig.schemaLocations)
                    schemaFileExtensions(pluginConfig.schemaFileExtensions)
                    introspection(pluginConfig.introspection)
                    builtInScalars(pluginConfig.builtInScalars)
                    // The call is the framework parameter this plugin owns: every route puts one in
                    // the operation context, so registering it here is what makes a resolver taking
                    // an ApplicationCall build instead of being told it needs @Argument.
                    contextParameter(ApplicationCall::class)
                    pluginConfig.interceptors.forEach { intercept(it) }
                    pluginConfig.errorBlocks.forEach { errors(it) }
                    pluginConfig.exceptionHandlers.forEach { exceptionHandler(it) }
                    val block = pluginConfig.schemaBlock ?: error("install(GraphQL) needs schema { … } or instance")
                    block()
                    pluginConfig.customizeBlock?.invoke(this)
                    pluginConfig.engineBlock?.let { engine(it) }
                }
        application.attributes.put(GraphixKey, engine)
        val path = pluginConfig.path
        val json = pluginConfig.json
        val subscriptions = pluginConfig.subscriptions
        // A pure function of the configuration, so it is built once here rather than per request.
        val sandbox = if (pluginConfig.sandbox) apolloSandboxPage(path, pluginConfig.sandboxEndpoint) else null
        val sandboxPath = pluginConfig.sandboxPath
        if (subscriptions == SubscriptionProtocol.GraphqlWs && application.pluginOrNull(WebSockets) == null) {
            application.install(WebSockets)
        }
        application.routing {
            graphqlRoute(path, engine, json, subscriptions)
            if (sandbox != null) sandboxRoute(sandboxPath, sandbox)
        }
        application.provideGraphix()
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
     * Whether every built-in scalar is in the schema. On by default, so `LocalDate`, `BigDecimal`
     * and the bounded numbers are there whether or not a field uses one. Off, the schema carries
     * only what a field resolved to. Ignored when [instance] is set: that engine already decided.
     */
    var builtInScalars: Boolean = true

    /**
     * Serves an Apollo Sandbox at [sandboxPath]. **Off by default** — installing this plugin opens a
     * GraphQL endpoint because that is what it is for; it should not also open an HTML page that
     * advertises the schema. Unlike [introspection], this is honoured even when [instance] is set:
     * the page is served by the plugin, not by the engine.
     */
    var sandbox: Boolean = false

    /** Where the sandbox page is served. Default `/sandbox`, a sibling of [path]. */
    var sandboxPath: String = "/sandbox"

    /**
     * GraphQL URL the sandbox opens with. Empty — the default — resolves it in the browser from the
     * page's own origin and [path], which is what survives a proxy, https and a republished port.
     */
    var sandboxEndpoint: String = ""

    /**
     * An engine built elsewhere. When set, [schema] is ignored. Whoever created it owns it —
     * graphql-java has no socket to close, so this plugin never calls `close`.
     */
    var instance: Graphix? = null

    /** How the HTTP envelope and GraphQL arguments are decoded. */
    var json: Json = lenientJson

    /**
     * Directories of `.graphqls` / `.gqls` files. Default `classpath:graphql/`, the same
     * place Spring GraphQL looks. Several files merge. An empty scan keeps the annotated schema.
     */
    var schemaLocations: List<String> = listOf("classpath:graphql/")

    /** File suffixes under [schemaLocations]. Default `.graphqls` and `.gqls`. */
    var schemaFileExtensions: List<String> = listOf(".graphqls", ".gqls")

    internal var schemaBlock: (GraphixBuilder.() -> Unit)? = null
    internal var customizeBlock: (GraphixBuilder.() -> Unit)? = null
    internal var engineBlock: GraphQLEngineCustomizer? = null
    internal val interceptors = mutableListOf<GraphixInterceptor>()
    internal val errorBlocks = mutableListOf<GraphixErrorSpec.() -> Unit>()
    internal val exceptionHandlers = mutableListOf<GraphixExceptionHandler>()

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

    /**
     * Wraps every operation: read the [ApplicationCall], put something in the operation context,
     * refuse the request, or shape the response.
     *
     * ```kotlin
     * install(GraphQL) {
     *     intercept {
     *         val user = call.principal<UserIdPrincipal>()
     *             ?: return@intercept flowOf(GraphixResult(null, listOf(GraphixError("unauthenticated"))))
     *         put(user)
     *         proceed()
     *     }
     *     schema { resolvers(ProductQueries(store)) }
     * }
     * ```
     *
     * Blocks run in the order they are written, outermost first, ahead of anything `schema { }`
     * registers. Each runs **once per operation** — so on a graphql-ws socket it sees every
     * `subscribe` frame, not just the handshake, and a credential that expires mid-socket is seen to
     * have expired.
     *
     * Cannot be combined with [instance]: interceptors live on the engine, and an adopted one
     * carries whatever was registered where it was built. The install refuses rather than ignoring.
     */
    fun intercept(block: suspend GraphixChain.() -> Flow<GraphixResult>) {
        interceptors += GraphixInterceptor { block() }
    }

    /** [intercept], with an interceptor written as a class. */
    fun intercept(interceptor: GraphixInterceptor) {
        interceptors += interceptor
    }

    /** graphql-java [GraphQLEngine.Builder] after the schema is built. */
    fun engine(block: GraphQLEngine.Builder.() -> Unit) {
        engineBlock = GraphQLEngineCustomizer(block)
    }

    /**
     * What a thrown exception becomes.
     *
     * ```kotlin
     * install(GraphQL) {
     *     errors {
     *         on<ProductNotFound> { failure -> error.withMessage("No product ${failure.id}").withErrorType(NOT_FOUND) }
     *     }
     *     schema { resolvers(ProductQueries(store)) }
     * }
     * ```
     *
     * Cannot be combined with [instance], for the same reason [intercept] cannot: handlers live on
     * the engine, so an adopted one already carries whatever was registered where it was built.
     */
    fun errors(block: GraphixErrorSpec.() -> Unit) {
        errorBlocks += block
    }

    /** [errors], with the handler already written as a class. Ktor's DI holds no beans to collect. */
    fun exceptionHandler(handler: GraphixExceptionHandler) {
        exceptionHandlers += handler
    }
}

/** Application attribute the [GraphQL] plugin writes. */
internal val GraphixKey = AttributeKey<Graphix>("com.softistx.graphix.Graphix")
