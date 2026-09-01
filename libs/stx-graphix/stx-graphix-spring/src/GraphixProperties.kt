package com.softistx.graphix.spring

import com.softistx.graphix.http.SubscriptionProtocol
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * What `stx.graphix` configures. Off unless asked for — putting this module on a classpath
 * must not open a GraphQL endpoint.
 */
@ConfigurationProperties(prefix = "stx.graphix")
data class GraphixProperties(
    /**
     * Serves POST/GET at [path] and builds a [com.softistx.graphix.Graphix] from
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
    /**
     * Directories of `.graphqls` / `.gqls` files. Default `classpath:graphql/`, Spring GraphQL's
     * location. Several files merge. An empty scan keeps the annotated schema.
     */
    val schemaLocations: List<String> = listOf("classpath:graphql/"),
    /** File suffixes under [schemaLocations]. Default `.graphqls` and `.gqls`. */
    val schemaFileExtensions: List<String> = listOf(".graphqls", ".gqls"),
    /**
     * Whether `__schema` and `__type` answer. On by default — GraphiQL and Apollo Sandbox need
     * them. Ignored when the application supplies its own `Graphix` bean.
     */
    val introspection: Boolean = true,
    /**
     * Serves an Apollo Sandbox at [sandboxPath]. Off by default: enabling GraphQL should not also
     * open an HTML page that advertises the schema.
     */
    val sandbox: Boolean = false,
    /** Where the sandbox page is served. Default `/sandbox`, a sibling of [path]. */
    val sandboxPath: String = "/sandbox",
    /**
     * GraphQL URL the sandbox opens with. Empty — the default — resolves it in the browser from the
     * page's own origin and [path], which is what survives a proxy, https and a republished port.
     */
    val sandboxEndpoint: String = "",
)
