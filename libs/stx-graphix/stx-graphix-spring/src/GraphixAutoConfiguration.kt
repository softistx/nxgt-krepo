package com.strange.graphix.spring

import com.strange.common.serialization.lenientJson
import com.strange.graphix.Graphix
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * GraphQL over WebFlux, opt-in behind `stx.graphix.enabled`.
 *
 * Collects every `@GraphQLController` bean as query/mutation roots. An application that already
 * has a [Graphix] bean keeps it — `@ConditionalOnMissingBean`.
 */
@AutoConfiguration
@EnableConfigurationProperties(GraphixProperties::class)
@ConditionalOnProperty(prefix = "stx.graphix", name = ["enabled"], havingValue = "true")
class GraphixAutoConfiguration {
    /**
     * One engine from every `@GraphQLController` bean. A class with both `@Query` and
     * `@Mutation` is registered as both roots. Skipped when the application already declared
     * a [Graphix] — that instance is the one the router uses.
     */
    @Bean
    @ConditionalOnMissingBean
    fun graphix(applicationContext: ApplicationContext): Graphix {
        val controllers = applicationContext.getBeansWithAnnotation<GraphQLController>().values
        return Graphix {
            controllers.forEach { addController(it) }
        }
    }

    /** POST and GET at [GraphixProperties.path]. Field errors stay HTTP 200. */
    @Bean
    fun graphixRouter(
        graphix: Graphix,
        properties: GraphixProperties,
    ): RouterFunction<ServerResponse> = GraphixHandler(graphix, lenientJson, properties.path).router()
}
