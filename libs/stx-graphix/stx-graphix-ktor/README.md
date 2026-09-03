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
`OrderService` on its constructor. What exists only for one request comes the other way: **the
plugin puts the `ApplicationCall` on every operation**, so a resolver takes one as a plain
parameter and an interceptor reads it as `call`.

```kotlin
install(GraphQL) {
    intercept {
        put(Caller(call.principal<UserIdPrincipal>()?.name ?: "anonymous"))
        proceed()
    }
    schema { resolvers(ProductQueries(store)) }
}

@QueryMapping
fun me(call: ApplicationCall): String = call.request.headers["X-User"] ?: "anonymous"
```

It is the same call over POST, over SSE and over graphql-ws — deliberately, so one resolver
signature works without knowing which transport it is on. On a socket it is the handshake's, that
being the only request a socket has; what changes mid-socket arrives in the client's
`connection_init` payload instead. [`docs/graphix.md`](../../../docs/graphix.md) has the full
table of what a resolver may see and the interceptor reference.

`instance` and `intercept { }` cannot both apply: interceptors live on the engine, so an adopted
one carries whatever was registered where it was built. The install refuses rather than ignoring
the blocks — a configuration field a framework quietly ignores is worse than one it refuses.

**Whoever created it closes it** still holds, and here it is almost nothing: graphql-java has no
socket. `instance` adopts an engine a container already built; the plugin does not close it.
Without `instance`, `schema { }` builds one at install.

`schemaLocations` defaults to `classpath:graphql/` — split `.graphqls` / `.gqls` files, merged.
An empty folder keeps the annotated schema.

`customize { }` is extra `GraphixBuilder` configuration (scalars, field directives) after
`schema { }`. `engine { }` customises graphql-java's builder.

**Collecting those from a container is not this plugin's job.** It used to be, behind `fromDi`,
reading Ktor DI. That is gone: assembling a schema asks *"give me every `GraphixInterceptor`"*, and
Ktor DI answers only *"give me the T"* — a `List<T>` resolves only if somebody registered that exact
list. `stx-graphix-koin`'s `fromKoin()` does the enumerating, in a container that supports it, and
reads the same under Ktor, under Spring, or with no server at all.

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
