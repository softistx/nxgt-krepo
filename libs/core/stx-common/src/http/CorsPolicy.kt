package com.softistx.common.http

import kotlinx.serialization.Serializable

/**
 * Which browsers may call this service, said once and understood by every framework here.
 *
 * ```kotlin
 * CorsPolicy(origins = listOf("http://localhost:5173"))
 * ```
 *
 * CORS is a browser policy, not a web-framework feature: the same list of origins means the same
 * thing behind Ktor and behind Spring, and an application moving between them should not have to
 * relearn its own configuration keys. `stx-ktor`'s `Cors` plugin and `stx-spring-boot`'s
 * `stx.cors` properties both build from this, so the two agree by construction rather than by
 * review.
 *
 * [origins] is empty by default because a browser policy that arrives already permitting somebody is
 * the wrong shape of default. The rest default permissively — once an origin is trusted, restricting
 * which *methods* it may use adds nothing that an attacker at that origin cannot work around anyway.
 */
@Serializable
data class CorsPolicy(
    /** The exact origins allowed. A single `"*"` is accepted, but see [allowCredentials]. */
    val origins: List<String> = emptyList(),
    /**
     * Origins matched as patterns, so one entry covers every subdomain of a domain.
     *
     * This is the one that works with [allowCredentials], because the browser is sent back the
     * concrete requesting origin rather than a wildcard.
     */
    val originPatterns: List<String> = emptyList(),
    /** The methods those origins may use. */
    val methods: List<String> = listOf("*"),
    /** The request headers they may send. */
    val headers: List<String> = listOf("*"),
    /** The response headers the browser is allowed to read. Empty means the CORS-safelisted ones. */
    val exposedHeaders: List<String> = emptyList(),
    /** Whether the browser may send cookies and `Authorization`. See the note on [originPatterns]. */
    val allowCredentials: Boolean = true,
    /** How long a browser may cache the preflight answer, in seconds. */
    val maxAgeSeconds: Long = 3600,
    /** The path pattern this policy applies to, for the frameworks that scope it by path. */
    val path: String = "/**",
) {
    /**
     * Refuses the one combination that builds, starts, and then fails on every preflight.
     *
     * A wildcard origin with credentials is forbidden by the CORS specification — a browser will not
     * send cookies to a wildcard — and the frameworks do not agree on when they notice. Spring
     * throws when the *request* arrives, which turns a configuration mistake into an intermittent
     * browser failure found by whoever is testing the front end, with a message in a log nobody was
     * reading. Checking here means every framework refuses it at the same moment: while the
     * application is starting.
     *
     * [patternsSetting] is what to name in the message — `stx.cors.origin-patterns` under Spring,
     * `originPatterns` in Kotlin. The useful half of this failure is what to do instead, and "what
     * to do instead" is spelled differently depending on where the policy was written.
     */
    fun validate(patternsSetting: String = "originPatterns"): CorsPolicy =
        apply {
            require(!(allowCredentials && WILDCARD in origins)) {
                "CORS: origins cannot be \"$WILDCARD\" while credentials are allowed — the CORS " +
                    "specification forbids it and a browser will refuse the response. Set " +
                    "$patternsSetting instead, which echoes the concrete requesting origin back, or " +
                    "disallow credentials."
            }
        }

    companion object {
        const val WILDCARD = "*"
    }
}
