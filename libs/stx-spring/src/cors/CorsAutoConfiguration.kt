package com.strange.spring.cors

import com.strange.common.http.CorsPolicy
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
 *
 * The properties become a [CorsPolicy] first and Spring's `CorsConfiguration` second. That is not
 * ceremony: `stx-ktor` installs Ktor's plugin from the same policy, so the two frameworks cannot
 * drift apart on what a given configuration means, and the one combination the CORS specification
 * forbids is refused in the same place for both.
 */
@AutoConfiguration
@EnableConfigurationProperties(CorsProperties::class)
@ConditionalOnProperty(prefix = "stx.cors", name = ["enabled"], havingValue = "true")
class CorsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun corsWebFilter(properties: CorsProperties): CorsWebFilter {
        val policy = properties.policy().validate(patternsSetting = "stx.cors.origin-patterns")
        return CorsWebFilter(
            UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration(policy.path, policy.toSpring()) },
        )
    }
}

/**
 * The policy as Spring expresses it.
 *
 * Written as a receiver parameter rather than inside an `apply`, because the two types have seven
 * field names in common: inside `apply`, `exposedHeaders` is the *Spring* object's property, and the
 * assignment reads as a copy while being a self-assignment. That is a compile error for the nullable
 * ones and silently correct-looking for the rest.
 *
 * An empty list becomes null rather than an empty list, because Spring reads the two differently:
 * null means "unset, fall back", an empty list means "nothing is allowed". A policy that never
 * mentioned exposed headers must not silently forbid the ones a browser already reads.
 */
private fun CorsPolicy.toSpring(): CorsConfiguration {
    val policy = this
    val spring = CorsConfiguration()
    spring.allowedOrigins = policy.origins.ifEmpty { null }
    spring.allowedOriginPatterns = policy.originPatterns.ifEmpty { null }
    spring.allowedMethods = policy.methods
    spring.allowedHeaders = policy.headers
    spring.exposedHeaders = policy.exposedHeaders.ifEmpty { null }
    spring.allowCredentials = policy.allowCredentials
    spring.maxAge = policy.maxAgeSeconds
    return spring
}
