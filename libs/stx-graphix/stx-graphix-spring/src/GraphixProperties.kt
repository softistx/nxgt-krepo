package com.strange.graphix.spring

import com.strange.graphix.http.SubscriptionProtocol
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * What `stx.graphix` configures. Off unless asked for — putting this module on a classpath
 * must not open a GraphQL endpoint.
 */
@ConfigurationProperties(prefix = "stx.graphix")
data class GraphixProperties(
    /**
     * Serves POST/GET at [path] and builds a [com.strange.graphix.Graphix] from
     * `@GraphQLController` beans. Off unless set.
     */
    val enabled: Boolean = false,
    /** HTTP path for POST and GET. The GraphQL protocol default, not a Graphix-specific name. */
    val path: String = "/graphql",
    /**
     * How subscriptions are served. `sse` (default) is `text/event-stream` on POST.
     * `graphql-ws` is a WebSocket on [path]; HTTP POST of a subscription is then 400.
     */
    val subscriptions: SubscriptionProtocol = SubscriptionProtocol.Sse,
)
