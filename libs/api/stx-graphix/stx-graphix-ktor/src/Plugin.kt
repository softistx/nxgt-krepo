package com.softistx.graphix.ktor

import com.softistx.graphix.*
import com.softistx.graphix.error.errors
import com.softistx.graphix.error.exceptionHandler
import com.softistx.graphix.http.SubscriptionProtocol
import com.softistx.graphix.http.apolloSandboxPage
import com.softistx.graphix.intercept.GraphixChain
import com.softistx.graphix.intercept.intercept
import com.softistx.graphix.schema.contextParameter
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*

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


/** Application attribute the [GraphQL] plugin writes. */
internal val GraphixKey = AttributeKey<Graphix>("com.softistx.graphix.Graphix")
