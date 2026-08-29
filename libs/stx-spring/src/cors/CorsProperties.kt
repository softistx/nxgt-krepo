package com.strange.spring.cors

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
 * The origins have no default and the list is deliberately empty: a browser policy that arrives
 * already permitting somebody is the wrong shape of default. Everything else defaults to the
 * permissive value, because once an origin is trusted, restricting which *methods* it may use adds
 * nothing an attacker cannot work around from that origin anyway.
 */
@ConfigurationProperties(prefix = "stx.cors")
data class CorsProperties(
    /** Registers the CORS filter. Off unless asked for, like every `stx.*` integration. */
    val enabled: Boolean = false,
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
    val maxAge: Long = 3600,
    /** The paths this configuration applies to. */
    val path: String = "/**",
)
