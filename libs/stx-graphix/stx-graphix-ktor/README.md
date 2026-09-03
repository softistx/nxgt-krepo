# stx-graphix-ktor

The Ktor plugin for `stx-graphix`. One engine per application, `POST` and `GET` at `/graphql`.

```kotlin
install(GraphQL) {
    schema {
        resolvers(
            ProductQueries(store),
            ProductMutations(store),
            ProductSubscriptions(store),
            ProductFields(reviews),
        )
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

`schemaLocations` defaults to `classpath:graphql/` — split `.graphqls` / `.gqls` files, merged.
An empty folder keeps the annotated schema.

`customize { }` is extra `GraphixBuilder` configuration (scalars, field directives) after
`schema { }`. `engine { }` customises graphql-java's builder. `fromDi = true` pulls
`GraphixCustomizer`, `GraphQLScalarType`, `GraphixDirective` and engine customizers from
Ktor DI — the same types Spring collects as beans.

Installing the plugin registers that same engine with Ktor DI, so a class the container builds takes
a `Graphix` in its constructor. It is not a flag.

`sandbox = true` serves an Apollo Sandbox at `/sandbox` — off by default, because installing this
plugin is a decision to open a GraphQL endpoint and not one to open a page advertising the schema.
`sandboxPath` moves it. The page finds `/graphql` on its own from the browser's origin, so it works
behind a proxy and in a container without configuration; `sandboxEndpoint` pins an absolute URL when
it should point elsewhere.

A GraphQL field error is HTTP 200 plus `errors[]`. Malformed JSON is HTTP 400. Introspection
(`{ __schema }`, `{ __type(name: …) }`) is on — GraphiQL and the sandbox talk to this path — and
`introspection = false` turns it off. Subscriptions
default to `text/event-stream` on the same path. Set `subscriptions = GraphqlWs` for
`graphql-ws` (`graphql-transport-ws` on that path); HTTP POST of a subscription is then 400.
The vocabulary is in [`docs/graphix.md`](../../../docs/graphix.md).
