package com.strange.spring.cors

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

/**
 * A CORS filter built from `stx.cors`, so the origins live in configuration rather than in a
 * hardcoded list somebody has to find and edit per environment.
 */
@AutoConfiguration
@EnableConfigurationProperties(CorsProperties::class)
@ConditionalOnProperty(prefix = "stx.cors", name = ["enabled"], havingValue = "true")
class CorsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun corsWebFilter(properties: CorsProperties): CorsWebFilter {
        properties.validate()
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins = properties.origins.ifEmpty { null }
                allowedOriginPatterns = properties.originPatterns.ifEmpty { null }
                allowedMethods = properties.methods
                allowedHeaders = properties.headers
                exposedHeaders = properties.exposedHeaders.ifEmpty { null }
                allowCredentials = properties.allowCredentials
                maxAge = properties.maxAge
            }
        return CorsWebFilter(
            UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration(properties.path, configuration) },
        )
    }
}

/**
 * Refuses the one combination that compiles, starts, and then fails on every preflight.
 *
 * `allowedOrigins = ["*"]` with `allowCredentials = true` is forbidden by the CORS specification —
 * a browser will not send cookies to a wildcard — and Spring throws when the *request* arrives, not
 * when the bean is built. That turns a configuration mistake into an intermittent browser failure
 * discovered by whoever is testing the front end, with a message about `allowedOrigins` in a log
 * nobody was reading. `originPatterns` is the answer, and this says so.
 */
private fun CorsProperties.validate() {
    require(!(allowCredentials && origins.contains("*"))) {
        "stx.cors: origins cannot be \"*\" while allow-credentials is true — the CORS specification " +
            "forbids it and a browser will refuse the response. Use stx.cors.origin-patterns, which " +
            "echoes the concrete requesting origin back, or set allow-credentials to false."
    }
}
