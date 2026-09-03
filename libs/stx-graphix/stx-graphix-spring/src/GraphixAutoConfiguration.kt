package com.softistx.graphix.spring

import com.softistx.common.serialization.lenientJson
import com.softistx.graphix.GraphQLEngineCustomizer
import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixCustomizer
import com.softistx.graphix.customize
import com.softistx.graphix.engine
import com.softistx.graphix.http.apolloSandboxPage
import com.softistx.graphix.scalar.scalar
import com.softistx.graphix.schema.GraphixDirective
import com.softistx.graphix.schema.fieldDirective
import graphql.schema.GraphQLScalarType
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter

/**
 * GraphQL over WebFlux, opt-in behind `stx.graphix.enabled`.
 *
 * Collects every `@GraphQLController` bean as query/mutation/subscription roots. An application
 * that already has a [Graphix] bean keeps it — `@ConditionalOnMissingBean`.
 */
@AutoConfiguration
@EnableConfigurationProperties(GraphixProperties::class)
@ConditionalOnProperty(prefix = "stx.graphix", name = ["enabled"], havingValue = "true")
class GraphixAutoConfiguration {
    /**
     * One engine from every `@GraphQLController` bean. A class with both `@QueryMapping` and
     * `@MutationMapping` is registered as both roots. Skipped when the application already declared
     * a [Graphix] — that instance is the one the router uses.
     */
    @Bean
    @ConditionalOnMissingBean
    fun graphix(
        applicationContext: ApplicationContext,
        properties: GraphixProperties,
    ): Graphix {
        val controllers = applicationContext.getBeansWithAnnotation<GraphQLController>().values
        val scalars = applicationContext.getBeansOfType(GraphQLScalarType::class.java).values
        val directives = applicationContext.getBeansOfType(GraphixDirective::class.java).values
        val customizers = applicationContext.getBeansOfType(GraphixCustomizer::class.java).values
        val engines = applicationContext.getBeansOfType(GraphQLEngineCustomizer::class.java).values
        return Graphix {
            schemaLocations(properties.schemaLocations)
            schemaFileExtensions(properties.schemaFileExtensions)
            introspection(properties.introspection)
            builtInScalars(properties.builtInScalars)
            resolvers(controllers)
            scalars.forEach { scalar(it) }
            directives.forEach { fieldDirective(it) }
            customizers.forEach { customize(it) }
            engines.forEach { engine(it) }
        }
    }

    /** POST and GET at [GraphixProperties.path]. Field errors stay HTTP 200. */
    @Bean
    fun graphixRouter(
        graphix: Graphix,
        properties: GraphixProperties,
    ): RouterFunction<ServerResponse> = GraphixHandler(graphix, lenientJson, properties.path, properties.subscriptions).router()

    /** The Apollo Sandbox page. Absent unless `stx.graphix.sandbox=true`. */
    @Bean
    @ConditionalOnProperty(prefix = "stx.graphix", name = ["sandbox"], havingValue = "true")
    fun graphixSandboxRouter(properties: GraphixProperties): RouterFunction<ServerResponse> =
        sandboxRouter(properties.sandboxPath, apolloSandboxPage(properties.path, properties.sandboxEndpoint))

    /** graphql-ws on [GraphixProperties.path]. Absent unless `stx.graphix.subscriptions=graphql-ws`. */
    @Bean
    @ConditionalOnProperty(prefix = "stx.graphix", name = ["subscriptions"], havingValue = "graphql-ws")
    fun graphixWebSocketHandler(graphix: Graphix): GraphixWebSocketHandler = GraphixWebSocketHandler(graphix, lenientJson)

    @Bean
    @ConditionalOnProperty(prefix = "stx.graphix", name = ["subscriptions"], havingValue = "graphql-ws")
    fun graphixWebSocketMapping(
        handler: GraphixWebSocketHandler,
        properties: GraphixProperties,
    ): HandlerMapping = GraphixUpgradeMapping(properties.path, handler)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "stx.graphix", name = ["subscriptions"], havingValue = "graphql-ws")
    fun graphixWebSocketHandlerAdapter(): WebSocketHandlerAdapter = WebSocketHandlerAdapter()
}
