package com.strange.spring.cors

import com.strange.common.http.CorsPolicy
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * What `stx.cors` configures.
 *
 * ```yaml
 * stx:
 *   cors:
 *     enabled: true
 *     origins: [ "http://localhost:5173" ]
 * ```
 *
 * The fields are `com.strange.common.http.CorsPolicy`'s, and [policy] is how they get there. Two
 * types rather than one because they answer different questions: a `@ConfigurationProperties` class
 * has to carry `enabled`, which is a Spring Boot idea and means nothing to Ktor, and it has to be a
 * flat set of bindable keys so that `stx.cors.origins` is a key and not `stx.cors.policy.origins`.
 *
 * What matters is that the *policy* is shared: `stx-ktor`'s `cors(policy)` takes the same type, so
 * an application moving between the two frameworks keeps its keys and its meaning. CORS is a browser
 * policy, not a web-framework feature.
 */
@ConfigurationProperties(prefix = "stx.cors")
data class CorsProperties(
    /** Registers the CORS filter. Off unless asked for, like every `stx.*` integration. */
    val enabled: Boolean = false,
    /** The exact origins allowed. A single `"*"` is accepted, but see [allowCredentials]. */
    val origins: List<String> = emptyList(),
    /** Origins matched as patterns. The one that works with [allowCredentials]. */
    val originPatterns: List<String> = emptyList(),
    /** The methods those origins may use. */
    val methods: List<String> = listOf("*"),
    /** The request headers they may send. */
    val headers: List<String> = listOf("*"),
    /** The response headers the browser is allowed to read. Empty means the CORS-safelisted ones. */
    val exposedHeaders: List<String> = emptyList(),
    /** Whether the browser may send cookies and `Authorization`. */
    val allowCredentials: Boolean = true,
    /** How long a browser may cache the preflight answer, in seconds. */
    val maxAge: Long = 3600,
    /** The paths this configuration applies to. */
    val path: String = "/**",
) {
    /** These properties as the framework-free policy every stx integration builds from. */
    fun policy(): CorsPolicy =
        CorsPolicy(
            origins = origins,
            originPatterns = originPatterns,
            methods = methods,
            headers = headers,
            exposedHeaders = exposedHeaders,
            allowCredentials = allowCredentials,
            maxAgeSeconds = maxAge,
            path = path,
        )
}
