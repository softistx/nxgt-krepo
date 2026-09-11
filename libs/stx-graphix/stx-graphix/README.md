# stx-graphix

GraphQL for a Kotlin coroutine service, over graphql-java 26. Annotated functions are the schema,
`@Serializable` types are the GraphQL types, and a resolver is a suspend function.

**`io.github.softistx:stx-graphix`** — [how to depend on it](../../../docs/consuming.md).

```kotlin
val graphql = Graphix {
    resolvers(ProductQueries(store), ProductMutations(store), ProductSubscriptions(store))
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
com.softistx.graphix            Graphix, GraphixRequest, GraphixResult, GraphixException
com.softistx.graphix.validation GraphixLimits and the `validation { }` builder
com.softistx.graphix.schema     @QueryMapping / @MutationMapping / @SubscriptionMapping and the SerialDescriptor walk
com.softistx.graphix.execute    the CompletableFuture bridge, argument binding, errors
com.softistx.graphix.scalar     the built-in scalars, one file each, and the `scalar { }` DSL
com.softistx.graphix.message    coercion-error text, by key and locale
com.softistx.graphix.json       JsonElement ⇄ the Java values graphql-java speaks
```

## A scalar is a file

`Long`, `Instant`, `Uuid`, the widths, the decimals, the dates, `Url`, `Locale`, `Json` and the
eight bounded numbers. Each is one file holding its `Coercing` and its `GraphQLScalarType`, and one
line in `BuiltInScalars`. Nothing else in the library enumerates them: the `KType` lookup, the
`SerialDescriptor` lookup, the SDL wiring and the schema's additional types all read that list, so
the twenty-fifth scalar costs what the third did.

They are all in the schema, used or not. A vocabulary is worth more whole: a client's generator
sees every scalar the service can speak, a field can be retyped without the schema growing one
underneath it, and a bounded scalar — `PositiveInt` and its seven relatives, which have no Kotlin
type to be reached by — is usable the moment a document names it. `builtInScalars(false)` is the
other answer, for a schema whose introspection is a published contract and whose size is part of
it; the scalars a field resolved to are then the only ones there.

What is *not* here is a `Money`. A scalar with a domain meaning belongs to the application, and
`scalar { }` is how it says so — [`docs/graphix.md`](../../../docs/graphix.md) has that half.

## An error message is a key

A coercion error is the only message this library produces that reaches an API **client** — a
schema that will not build throws at startup, in one language, at whoever wrote it. So it is looked
up by key and by the operation's locale, and the locale is the client's: both HTTP integrations
negotiate it from `Accept-Language`.

The keys are generic, `{scalar}` carrying which scalar failed, so a new scalar needs no new key and
no new translation. English and French ship in the jar; another language is a properties file on
the application's classpath; another *source* — ICU, a database — is one lambda, which is where
`stx-i18n` plugs in.

It is not a dependency on `stx-i18n` for the same reason `stx-i18n` is not in `stx-common`: ICU4J
is a 15 MB jar, and a service that never translates anything should not carry a message formatter
to run GraphQL.

## A thrown exception is not an answer

Without a handler, whatever a resolver threw becomes the client's `message` — the ORM's, the HTTP
client's, the one with the table name in it. That is a leak by default, and no classification comes
with it, so a client cannot tell "you sent nonsense" from "we broke".

`errors { }` is where an application says otherwise, and its shape is chosen so that saying *less*
is the cheap thing:

```kotlin
errors {
    on<ProductNotFound> { failure -> error.withMessage("No product ${failure.id}").withErrorType(NOT_FOUND) }
    fallback { error.withMessage("Internal error").withErrorType(INTERNAL_ERROR) }
}
```

`error` arrives already filled — message, `path`, `locations` — so a handler that only classifies
restates nothing, the way `GraphqlErrorBuilder<?>` arrives filled in Spring GraphQL's
`@GraphQlExceptionHandler`. It is a `data class`, so `copy()` is the builder and the `withX`
extensions are names for the copies.

**Nothing changes until a handler claims it.** Returning `null` means *not mine* and the next
handler is asked; an exception nobody claims keeps exactly the answer it would have had. Registering
a handler for one exception type is not a decision about every other one — `fallback { }` is how an
application says it wants all of them. That is what makes the feature purely additive: adding
`errors { }` to an existing engine cannot change an error it does not mention.

There is **one** mechanism, not two. `GraphixExceptionHandler` is a single function taking the
error and the exception, and `on<T> { }` is sugar over one that declines anything but `T`. A single
function is what a container can be asked for, which is why a Spring `@Bean` and a Koin single are
found without a second form to learn.

Three seats, because a throw does not always have a field: the data fetcher, the interceptor chain,
and a subscription `Flow` that throws mid-stream. The last two were holes before this — an
interceptor throw left `execute` as a 500, and a mid-stream throw escaped `subscribe` raw for SSE to
lose. [`docs/graphix.md`](../../../docs/graphix.md) has the table and the one graphql-java trap
worth knowing.

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

That is also why abstract types resolve on a **name**. A `sealed` hierarchy is a GraphQL
`interface` when its subclasses share properties and a `union` when they do not — the shape of the
Kotlin code, not a second annotation — and at execute time the value is the Kotlin instance, so the
discriminator kotlinx.serialization would have written is not there to read. The runtime class's
name is the answer, which is the same convention every other type here follows and is why an SDL
`union` needs no wiring at all. `@GraphQLUnion` forces the union direction; `typeResolver(name) { }`
replaces the naming rule.

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

Under `graphql-ws` a socket has no per-request headers — it has one handshake, and nothing after
it. So a credential arrives in the protocol's own `connection_init` frame, and Graphix hands the
payload over as `GraphqlWsInit`. It is read per operation rather than per connection, which is what
makes a credential expiring mid-socket observable instead of a socket keeping whatever it was
authorised with at the upgrade. A field reachable from more than one transport reads it in an
interceptor, not as a resolver parameter, because a resolver parameter of a socket-only type fails
everywhere else. [`docs/graphix.md`](../../../docs/graphix.md) has the shape.

The same `Flow<T>` on a **query** is a list, not a stream, and the annotation is the whole of the
difference. It is collected before graphql-java sees anything — sugar for the `.toList()` the
resolver would otherwise write, because graphql-java 26 defines a `defer` directive and no `stream`
one, so nothing could be delivered incrementally even if the shape suggested it.

The reason to accept it at all is not keystrokes. It is that `stx-mongo`'s `findAll` returns a
`Flow` and `stx-jpa`'s a `List`, so without this a resolver body differs by backing store for no
GraphQL-level reason. And doing the collection here rather than in the resolver puts it on the
operation's scope, where cancelling the request cancels the source, and inside the data fetcher,
where a throw becomes an `errors[]` entry instead of escaping.

`maxListElements(n)` bounds it, and is unset by default because a `List` return has always been
unbounded too. What it buys is the one thing a `List` made impossible: a `Flow` can be infinite, and
an infinite one does not fail — it never answers. It bounds emission, not silence.

Field errors stay GraphQL errors. HTTP 200 plus `errors[]` is the spec; throwing out of `execute`
is for a document that cannot even be submitted.

## No resolver scan in core

`Graphix { resolvers(productQueries, productMutations, productFields) }`. Spring may collect
`@GraphQLController` beans; that is the Spring module's job. A classpath walk for *classes* would
make a worker with no Spring carry one.

**One call, not four.** `@QueryMapping` already says the function is a query, so a `query(...)`
beside it was the caller repeating the annotation — and it made a class holding a query and a
mutation something you had to register twice, or half of it disappeared. The instances are still
**named**: what is refused here is scanning for them, not the second sentence about what they are.
The trade is that a class is registered whole; exposing its queries but not its mutations means
splitting the class, which is the answer SOLID would have given anyway.

Schema **documents** are the other scan, and it is the Spring GraphQL one: `classpath:graphql/`,
every `.graphqls` / `.gqls` file, merged. Present files are the schema; annotated functions
are the DataFetchers. No files, the `@Serializable` types stay the schema.

Custom scalars and field directives are declared on the builder (`scalar { }`,
`fieldDirective { }`). The lambdas see the operation `GraphQLContext`. Spring collects
`GraphQLScalarType`, `GraphixDirective`, `GraphixCustomizer`, `GraphixInterceptor` and
`GraphQLEngineCustomizer` beans; Ktor's `customize { }` / `intercept { }` declares them inline,
and `stx-graphix-koin`'s `fromKoin()` collects the same list out of a Koin container.

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
on the mapping class. GraphQL arguments are `@Argument`. A field that needs this field's DFE
takes `dfe: DataFetchingEnvironment` by type. `@BatchMapping` may take `@Argument` too — the
DataLoader key is the parent plus those values.

Per-request state is the other bag. A parameter whose type was registered with
`contextParameter(...)` reads `Graphix.execute(..., context)` by `KClass`. Mixing the two is the usual mistake: looking up `ApplicationContext` from a resolver
to find `OrderService`, or putting `OrderService` in the operation context because it "feels
like DI". The first is a service locator. The second makes a singleton look request-scoped.

`intercept { }` is what fills that bag. A `GraphixInterceptor` wraps one operation — rewrite the
request, `put` something in the context, refuse it, or shape the response — and `proceed()` is the
rest of the chain. It returns a `Flow<GraphixResult>` whatever the operation kind, one element for
a query and N for a subscription, so a single interceptor serves both entry points and both HTTP
transports rather than needing a streaming twin.

Which types a resolver may take unannotated is **registered, not guessed**:
`contextParameter(SomeType::class)` says a type is supplied rather than asked for. This module
registers none — `DataFetchingEnvironment` and graphql-java's own `GraphQLContext` are known
because they are graphql-java's, and `ApplicationCall` and `ServerWebExchange` are registered by
`stx-graphix-ktor` and `stx-graphix-spring`. That is why neither framework appears in this
module's dependencies, and why a third one would be a call rather than a change here.

[`docs/graphix.md`](../../../docs/graphix.md) has the full table of what a resolver may see, and
the interceptor reference.

## What this slice does not do

Code generation, a GraphQL skill, Federation, a client. Those are later phases.

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
