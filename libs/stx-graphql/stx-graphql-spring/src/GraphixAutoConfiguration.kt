package com.strange.graphql.spring

import com.strange.common.serialization.lenientJson
import com.strange.graphql.Graphix
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * GraphQL over WebFlux, opt-in behind `stx.graphql.enabled`.
 *
 * Collects every `@GraphQLController` bean as query/mutation roots. An application that already
 * has a [Graphix] bean keeps it — `@ConditionalOnMissingBean`.
 */
@AutoConfiguration
@EnableConfigurationProperties(GraphixProperties::class)
@ConditionalOnProperty(prefix = "stx.graphql", name = ["enabled"], havingValue = "true")
class GraphixAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun graphix(applicationContext: ApplicationContext): Graphix {
        val controllers = applicationContext.getBeansWithAnnotation(GraphQLController::class.java).values
        return Graphix {
            controllers.forEach { addController(it) }
        }
    }

    @Bean
    fun graphixRouter(
        graphix: Graphix,
        properties: GraphixProperties,
    ): RouterFunction<ServerResponse> = GraphixHandler(graphix, lenientJson, properties.path).router()
}
