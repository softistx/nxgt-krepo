# stx-graphql

GraphQL for a Kotlin coroutine service, over graphql-java 25. Annotated functions are the schema,
`@Serializable` types are the GraphQL types, and a resolver is a suspend function.

```kotlin
val graphql = Graphix {
    query(ProductQueries(store))
    mutation(ProductMutations(store))
}

val result = graphql.execute(GraphixRequest("{ product(id: \"p1\") { name } }"))
```

Ktor and Spring Boot integrations live in `stx-graphql-ktor` and `stx-graphql-spring`. This module
does not know a web framework.

**This file answers *why the library is shaped this way*.** The annotation vocabulary — what a
schema may say — lives in [`docs/graphql.md`](../../../docs/graphql.md).

## Shape

```
com.strange.graphql            Graphix, GraphixRequest, GraphixResult, GraphixException
com.strange.graphql.schema     @Query / @Mutation and the SerialDescriptor walk
com.strange.graphql.execute    the CompletableFuture bridge, argument binding, errors
com.strange.graphql.scalar     Long, Instant, Uuid
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

Field errors stay GraphQL errors. HTTP 200 plus `errors[]` is the spec; throwing out of `execute`
is for a document that cannot even be submitted.

## No scan in core

`Graphix { query(instance); mutation(instance) }`. Spring may scan `@GraphQLController` beans;
that is the Spring module's job. A classpath walk in this type would make a worker with no
Spring carry one.

## What this slice does not do

Code generation, a GraphQL skill, subscriptions, DataLoader / type field resolvers, schema-first
SDL, Federation, a client. Those are later phases.
