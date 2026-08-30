package com.strange.graphql.spring

import com.strange.common.serialization.lenientJson
import com.strange.graphql.GraphQl
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
 * has a [GraphQl] bean keeps it — `@ConditionalOnMissingBean`.
 */
@AutoConfiguration
@EnableConfigurationProperties(GraphQlProperties::class)
@ConditionalOnProperty(prefix = "stx.graphql", name = ["enabled"], havingValue = "true")
class GraphQlAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun graphQl(applicationContext: ApplicationContext): GraphQl {
        val controllers = applicationContext.getBeansWithAnnotation(GraphQLController::class.java).values
        return GraphQl {
            controllers.forEach { addController(it) }
        }
    }

    @Bean
    fun graphQlRouter(
        graphQl: GraphQl,
        properties: GraphQlProperties,
    ): RouterFunction<ServerResponse> = GraphQlHandler(graphQl, lenientJson, properties.path).router()
}
