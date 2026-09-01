package com.softistx.spring.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * What `stx.security` configures.
 *
 * Note what is *not* here: no filter chain, no authentication manager, no permitted paths. Which
 * endpoints are open and how a user proves who they are is the application's policy, and a library
 * that guesses at it either locks a service out of its own health check or opens something that
 * should not be open. This contributes the two pieces every application would otherwise copy.
 */
@ConfigurationProperties(prefix = "stx.security")
data class SecurityProperties(
    /**
     * Registers the `PasswordEncoder` and the meta-annotation template support. Off unless asked
     * for, like every `stx.*` integration.
     */
    val enabled: Boolean = false,
    /**
     * The BCrypt work factor. Each step doubles the time to hash *and* to attack, which is the whole
     * point of the algorithm; Spring's own default is 10 and this follows it. Raising it does not
     * invalidate existing hashes — the cost is stored in the hash, so old ones keep verifying at the
     * strength they were written with.
     */
    val bcryptStrength: Int = 10,
)
