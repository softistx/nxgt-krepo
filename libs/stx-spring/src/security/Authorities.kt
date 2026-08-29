package com.strange.spring.security

/**
 * The prefixes Spring Security expects, written once.
 *
 * `hasRole("ADMIN")` matches the authority `ROLE_ADMIN`, and a `GrantedAuthority` built as `"ADMIN"`
 * therefore never matches it — a mismatch that produces no error anywhere, only a 403 for a user who
 * plainly has the role. Building authorities through these two puts the prefix in the one place it
 * can be got wrong.
 */
object Authorities {
    /** `ADMIN` as the authority `hasRole("ADMIN")` will look for. */
    fun role(name: String): String = "$ROLE_PREFIX$name"

    /** The same idea for a group, which Spring Security's group support prefixes this way. */
    fun group(name: String): String = "$GROUP_PREFIX$name"

    const val ROLE_PREFIX = "ROLE_"
    const val GROUP_PREFIX = "GROUP_"
}
