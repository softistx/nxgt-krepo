# stx-graphix

GraphQL for a Kotlin coroutine service, over graphql-java 26. Annotated functions are the schema,
`@Serializable` types are the GraphQL types, and a resolver is a suspend function.

```kotlin
val graphql = Graphix {
    query(ProductQueries(store))
    mutation(ProductMutations(store))
    subscription(ProductSubscriptions(store))
}

val result = graphql.execute(GraphixRequest("{ product(id: \"p1\") { name } }"))
graphql.subscribe(GraphixRequest("subscription { productAdded { name } }"))
```

Ktor and Spring Boot integrations live in `stx-graphix-ktor` and `stx-graphix-spring`. This module
does not know a web framework.

**This file answers *why the library is shaped this way*.** The annotation vocabulary — what a
schema may say — lives in [`docs/graphix.md`](../../../docs/graphix.md).

## Shape

```
com.strange.graphix            Graphix, GraphixRequest, GraphixResult, GraphixException
com.strange.graphix.validation GraphixLimits and the `validation { }` builder
com.strange.graphix.schema     @QueryMapping / @MutationMapping / @SubscriptionMapping and the SerialDescriptor walk
com.strange.graphix.execute    the CompletableFuture bridge, argument binding, errors
com.strange.graphix.scalar     Long, Instant, Uuid, and the `scalar { }` DSL
```

## Why not graphql-kotlin

Expedia's library is Jackson-first and scans. This repo's JSON is kotlinx.serialization, a
resolver is a coroutine, and the application **names** its roots the way it names a Ktor plugin
instance. Wrapping graphql-kotlin would import a second JSON stack and a second idea of where
types come from.

graphql-java is the engine. We do not rebuild it.

## SerialDescriptor is the type system

A `@Serializable` data class becomes a GraphQL object (or input object). Nullability, lists and
enums come off the descriptor. A type with no serializer fails schema build naming that Kotlin
type, not as a `Map` three layers down.

Property getters are how nested objects are read at execute time — graphql-java's own
`PropertyDataFetcher`. What is in memory is what the schema advertised.

## A resolver suspends

graphql-java speaks `CompletableFuture`. `kotlinx.coroutines.future.future` is the bridge, the
same one `stx-jpa` uses. A `CoroutineScope` lives in the operation's `GraphQLContext` and is
cancelled when `execute` returns. `runBlocking` on the engine thread is the thing that deadlocks
a client against its own I/O; it is not used here.

A **subscription** is a `Flow<T>` (or a reactive-streams `Publisher<T>`) on `@SubscriptionMapping`.
graphql-java wants a `Publisher`; `Flow.asPublisher` on the operation scope is the bridge, and
`Graphix.subscribe` is a `Flow<GraphixResult>` back. Cancelling the collector cancels the
upstream. `execute` on a subscription throws — the engine result is a stream, not one JSON
object.

Field errors stay GraphQL errors. HTTP 200 plus `errors[]` is the spec; throwing out of `execute`
is for a document that cannot even be submitted.

## No resolver scan in core

`Graphix { query(instance); mutation(instance); subscription(instance); type(instance) }`. Spring may collect `@GraphQLController` beans;
that is the Spring module's job. A classpath walk for *classes* would make a worker with no
Spring carry one.

Schema **documents** are the other scan, and it is the Spring GraphQL one: `classpath:graphql/`,
every `.graphqls` / `.gqls` file, merged. Present files are the schema; annotated functions
are the DataFetchers. No files, the `@Serializable` types stay the schema.

Custom scalars and field directives are declared on the builder (`scalar { }`,
`fieldDirective { }`). The lambdas see the operation `GraphQLContext`. Spring collects
`GraphQLScalarType`, `GraphixDirective`, `GraphixCustomizer` and `GraphQLEngineCustomizer`
beans; Ktor's `customize { }` / `fromDi = true` is the same list from DI.

## The data fetcher is not yours

graphql-java wants a `DataFetcher`. Graphix builds one per `@QueryMapping` / `@MutationMapping` / `@SubscriptionMapping` /
`@SchemaMapping` / `@BatchMapping` and never hands it out. The fetcher's job is to call the function on the
**instance already registered** — `query(productQueries)` / `type(productFields)` — and to bind
arguments. That is why a Spring mutation that needs `OrderService` takes it on the controller
constructor: the controller *is* the Spring bean, and the fetcher holds that bean for the life
of the engine.

`@SchemaMapping` is a per-parent resolver. `@BatchMapping` is the same field behind a DataLoader
(`List<Book>` → `Map<Book, Author>`) and registers the field — do not also put `@SchemaMapping`
on it. To `load()` by key from a `@SchemaMapping` (arguments included), declare `dataLoader { }`
on the mapping class. A field that needs this field's DFE takes
`@GraphQLContext dfe: DataFetchingEnvironment`.

Per-request state is the other bag. `@GraphQLContext` reads `Graphix.execute(..., context)` by
`KClass`. Mixing the two is the usual mistake: looking up `ApplicationContext` from a resolver
to find `OrderService`, or putting `OrderService` in the operation context because it "feels
like DI". The first is a service locator. The second makes a singleton look request-scoped.

[`docs/graphix.md`](../../../docs/graphix.md) has the three columns a resolver may see.

## What this slice does not do

Code generation, a GraphQL skill, Federation, a client. Those are later phases.
