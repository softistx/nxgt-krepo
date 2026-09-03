# stx-graphix-koin

Builds a `stx-graphix` schema from what a Koin container holds.

```kotlin
@Singleton
class ProductQueries(private val store: Store) : GraphixResolver {
    @QueryMapping
    suspend fun products(): List<Product> = store.all()
}

@Singleton
class Authentication : GraphixInterceptor {
    override suspend fun GraphixChain.intercept(): Flow<GraphixResult> {
        put(Caller(call.principal<UserIdPrincipal>()?.name ?: "anonymous"))
        return proceed()
    }
}

install(GraphQL) {
    schema { fromKoin() }
}
```

`fromKoin()` is a `GraphixBuilder` extension, not a plugin flag, so it is the same call under Ktor,
under Spring, or in a plain `Graphix { }`. This module depends on `koin-core` and `stx-graphix` and
on no server.

## Why collection needs Koin rather than a DI lookup

Assembling a schema asks the container *"give me every `GraphixInterceptor`"*. That is a different
question from *"give me the `Store`"*, and most containers only answer the second. Koin answers both:
`Koin.getAll<T>()` enumerates every single bound to `T`, and koin-annotations binds a class to the
interfaces it implements, so a `@Singleton` needs nothing else to be found.

## Why a marker interface and not `@GraphQLController`

Six of the seven things collected here are **already types** — `GraphixCustomizer`,
`GraphixDirective`, `GraphixInterceptor`, `GraphixExceptionHandler`, `GraphQLScalarType`,
`GraphQLEngineCustomizer` — so the type is the marker and no annotation would add anything. Only a resolver is an ordinary class carrying `@QueryMapping`
functions, with nothing in common with the next one, and Koin cannot be queried by annotation.
Hence `GraphixResolver`, implemented rather than annotated.

An annotation would have been nicer still — `@Singleton annotation class GraphQLController`, one
mark instead of two. **It does not work.** Koin's compiler plugin matches direct annotations only,
so a meta-annotated class compiles and is then silently absent from the container; measured on Koin
4.2.2 with koin-compiler-plugin 1.1.0, where a meta-annotated single resolved to zero definitions
while a directly annotated one resolved to one. Spring's `@GraphQLController` works because Spring
reads annotation metadata at runtime, where meta-annotations are visible; a compile-time processor
sees only what is written on the class.

## Ordering

`getAll` returns definitions in declaration order, and interceptors run outermost-first in the order
they were added — so an interceptor declared before another in the Koin module wraps it. Mixing
`fromKoin()` with explicit registration is ordinary; whichever call comes first is outermost.

The same order decides among `GraphixExceptionHandler` singles: each is asked in turn and the first
to answer wins, so a handler for a specific exception is declared before a broader one that would
also claim it. A `fallback { }` is asked last whatever the order — it has to be, since nothing
decides the order a container's singles were declared in across modules.

[`docs/graphix.md`](../../../docs/graphix.md) has the interceptor and exception-handler reference,
and the table of what a resolver may see.
