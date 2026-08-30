package com.strange.graphix.spring

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * What `stx.graphix` configures. Off unless asked for — putting this module on a classpath
 * must not open a GraphQL endpoint.
 */
@ConfigurationProperties(prefix = "stx.graphix")
data class GraphixProperties(
    /** Serves POST/GET at [path]. */
    val enabled: Boolean = false,
    /** HTTP path. Default `/graphql`. */
    val path: String = "/graphql",
)
