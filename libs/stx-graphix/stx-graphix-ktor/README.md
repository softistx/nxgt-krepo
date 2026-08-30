# stx-graphix-ktor

The Ktor plugin for `stx-graphix`. One engine per application, `POST` and `GET` at `/graphql`.

```kotlin
install(GraphQL) {
    schema {
        query(ProductQueries(store))
        mutation(ProductMutations(store))
    }
}
```

**Whoever created it closes it** still holds, and here it is almost nothing: graphql-java has no
socket. `instance` adopts an engine a container already built; the plugin does not close it.
Without `instance`, `schema { }` builds one at install.

`injectable = true` registers that same engine with Ktor DI (`provideGraphix()`), off by default
because `ktor-server-di` is compile-only.

A GraphQL field error is HTTP 200 plus `errors[]`. Malformed JSON is HTTP 400. The vocabulary
is in [`docs/graphix.md`](../../../docs/graphix.md).
