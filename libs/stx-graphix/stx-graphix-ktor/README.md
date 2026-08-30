# stx-graphix-ktor

The Ktor plugin for `stx-graphix`. One engine per application, `POST` and `GET` at `/graphql`.

```kotlin
install(GraphQL) {
    schema {
        query(ProductQueries(store))
        mutation(ProductMutations(store))
        subscription(ProductSubscriptions(store))
        type(ProductFields(reviews))
    }
}
```

`store` is closed over when the engine is built, the same way a Spring controller takes
`OrderService` on its constructor. Per-request values (`ApplicationCall`, a principal) belong in
`Graphix.execute(..., context)` and `@GraphQLContext`. The plugin does not yet forward the call
into that map — [`docs/graphix.md`](../../../docs/graphix.md) has the three columns a resolver
sees.

**Whoever created it closes it** still holds, and here it is almost nothing: graphql-java has no
socket. `instance` adopts an engine a container already built; the plugin does not close it.
Without `instance`, `schema { }` builds one at install.

`injectable = true` registers that same engine with Ktor DI (`provideGraphix()`), off by default
because `ktor-server-di` is compile-only.

A GraphQL field error is HTTP 200 plus `errors[]`. Malformed JSON is HTTP 400. A subscription
is `text/event-stream` on the same path, one `data:` frame per event. The vocabulary is in
[`docs/graphix.md`](../../../docs/graphix.md).
