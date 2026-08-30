package com.strange.graphix.http

/**
 * How Graphix serves `@SubscriptionMapping` over HTTP.
 *
 * [Sse] is POST/GET on the GraphQL path as `text/event-stream`. [GraphqlWs] is the
 * `graphql-transport-ws` sub-protocol on the same path as a WebSocket; HTTP POST of a
 * subscription document is then 400.
 */
enum class SubscriptionProtocol {
    Sse,
    GraphqlWs,
}
