# Writing a stx-graphix resolver

The resolver half of the `stx-graphix` vocabulary: fields and arguments, what a resolver may take as
a parameter, interceptors, exception handlers and building the schema from a Koin container. The
schema half (roots, types, scalars, validation) is [`graphix.md`](graphix.md); serving it
(documents, `execute`, HTTP, SSE, graphql-ws) is [`graphix-serving.md`](graphix-serving.md).

## Fields and arguments

| Annotation | Where | Effect |
| --- | --- | --- |
| `@GraphQLName("foo")` | **class only** | GraphQL type name. A property is renamed with `@SerialName`, an argument with `@Argument(name=…)`, a field with its own mapping annotation — one owner per name |
| `@GraphQLDescription("…")` | same | GraphQL description |
| `@GraphQLIgnore` | property | omitted from the GraphQL type |
| `@Argument("foo")` | parameter | **Required** on every GraphQL argument. [name] defaults to the Kotlin parameter name |
| `@Directive("name")` | mapping function | wraps the field with the `fieldDirective("name")` registered on the builder |
| `@GraphQLDeprecated("why")` | function, property, parameter | GraphQL `@deprecated`. Kotlin's own `@Deprecated` is `BINARY`-retained and unreadable by reflection, hence a second annotation. An argument or input field may only carry it when it is **not required** — nullable, or non-null with a `@GraphQLDefault`; the spec forbids deprecating one a caller has no way to stop sending |
| `@GraphQLOneOf` | class used as an input | GraphQL `@oneOf`: exactly one field, and not null |
| `@GraphQLId` | function, property, parameter | GraphQL `ID` instead of `String` / `Uuid` / `Long` |
| `@GraphQLDefault("10")` | parameter, input-object property | the GraphQL default, as a literal |

`@GraphQLId` is a serialisation hint, not a Kotlin type: the value stays a `String` on both sides,
and on a `List<String>` the annotation carries down to the element (`[ID!]!`). Anything but
`String`, `Uuid` or `Long` fails schema build naming the type. On an **SDL** schema write `ID` in
the document instead — graphql-java coerces it to a `String` and the resolver never notices, so
this annotation is for the annotation-derived schema only.

`@GraphQLDefault` is what puts a default *in the schema*. A Kotlin default alone only makes the
argument optional — graphql-java has no notion of one, so `@Argument limit: Int = 10` reaches a
client as `limit: Int`, nullable and with nothing to read. With the annotation it is
`limit: Int! = 10`: the non-null the Kotlin signature actually promises, and a value introspection
can report. graphql-java then supplies it, so the Kotlin default never runs — **the two must
agree**. A literal that does not parse fails schema build naming the argument, and a `@GraphQLOneOf`
field may not carry one at all.

A deprecated field stays in the schema and keeps resolving; introspection hides it unless the
query asks (`fields(includeDeprecated: true)`). An **argument** or **input field** may only be
deprecated when it is optional — the spec forbids deprecating one a caller has to send, and
Graphix says so at schema build rather than letting graphql-java reject the schema.

`@GraphQLOneOf` is GraphQL's answer to the input union a sealed hierarchy cannot be:

```kotlin
@GraphQLOneOf
@Serializable
data class PickInput(
    val byId: String? = null,
    val byName: String? = null,
)
```

Every field must be nullable — that is what lets a caller send only one — and a non-nullable one
fails schema build naming it. The `= null` defaults are not decoration: graphql-java coerces a
oneOf input to a map holding only the field that was given, so a property with neither a default
nor a value has nothing to be constructed from. Sending two fields, none, or one that is `null` is
a GraphQL error, enforced by graphql-java. An SDL `input X @oneOf` behaves identically.

`@Argument` is required on every GraphQL argument — a resolver parameter. Unmarked parameters
are not arguments: the parent source (`SchemaMapping` / `BatchMapping` first parameter), this
field's `DataFetchingEnvironment`, and any type registered with `contextParameter(...)` — all by
type, see [Framework parameters](#framework-parameters). An input object's
fields are not arguments either: `CreateProductInput` is the `@Argument`, `name` and `tags`
are its fields (`@SerialName` / `@GraphQLIgnore` still apply). A Kotlin default on an
`@Argument` parameter, or on an input-object property, is optional GraphQL.

Nested object fields are the `@Serializable` properties already in memory. Extra fields that need
I/O are `@SchemaMapping` or `@BatchMapping` on a registered instance. **A given
field is one or the other, not both.** `@BatchMapping` registers the field itself — no
`@SchemaMapping` beside it.

```kotlin
class BookFields(
    private val authors: AuthorStore,
) {
    @SchemaMapping(typeName = "Book", field = "titleUpper")
    fun titleUpper(book: Book): String = book.title.uppercase()

    @BatchMapping
    suspend fun author(books: List<Book>): Map<Book, Author> = authors.forBooks(books)

    @BatchMapping
    suspend fun snippets(books: List<Book>, @Argument limit: Int): Map<Book, List<String>> =
        authors.snippets(books, limit)
}
```

`typeName` defaults to the simple name of the first argument's type (`Book`). `field` defaults
to the function name (`author`). `@BatchMapping` takes `List<Parent>` and returns
`Map<Parent, T>` (or `List<T>` in key order). A single parent is N+1; Graphix refuses it.
`@Argument` parameters are GraphQL arguments: the DataLoader key is the parent plus those
values, so two aliases of the same field with different arguments do not share a cached row.
A DataLoader is **per GraphQL operation**, not per HTTP request — two concurrent operations
do not batch together.

**A property is read only when its field is selected.** Object fields are fetched one at a time by
graphql-java's `PropertyDataFetcher`; nothing here serialises a source object wholesale, so a
property outside the selection is never touched. And a property outside the `@Serializable`
descriptor — `@GraphQLIgnore`, or `@Transient` — has no field to select at all, which is the stronger
guarantee of the two. `UnselectedFieldTest` pins both against a property that throws when read.

The two are not the same protection. A field that exists and throws runs **every other resolver
first** and fails during execution; a field that is not in the schema is a `ValidationError` — the
document is rejected whole, `data` is `null`, and **no resolver runs at all**. Null-bubbling hides
that difference in the response when the failing field is non-nullable, so it is measured by
counting what ran, not by reading the answer.

That matters when the parent is a JPA entity, because there a property that throws when read is not
a fixture: an unfetched `LAZY` association is exactly that. So the DataLoader keys on the **foreign
key column** beside the association rather than on the association itself, and `@GraphQLIgnore` on
the association turns "nobody selected it" into "nobody can". `@BatchMapping` receives the parent
instances, so it reads that column whether or not the column is itself a field.
[`docs/jpa-mapping.md`](jpa-mapping.md) has the mapping, its two mandatory flags, and the one way it
bites.

A `@SchemaMapping` that needs this field's source or arguments takes
`dfe: DataFetchingEnvironment` (no annotation). That DFE is **this field**, not an entry in
`execute`'s context map.

To load by key (including field arguments) from a `@SchemaMapping`, declare a `dataLoader` on
the mapping class and `load()` it. The key type may be a `data class` that carries arguments:

```kotlin
data class ReviewKey(val bookId: String, val limit: Int)

class BookFields(private val store: ReviewStore) {
    val authors = dataLoader<String, Author> { ids -> store.authors(ids) }
    val reviews = dataLoader<ReviewKey, List<Review>> { keys, env ->
        store.reviews(keys, env.graphQlContext)
    }

    @SchemaMapping
    suspend fun author(book: Book): Author? = authors.load(book.authorId)

    @SchemaMapping
    suspend fun reviews(book: Book, @Argument limit: Int = 10): List<Review> =
        reviews.load(ReviewKey(book.id, limit)).orEmpty()
}
```

Graphix registers those properties per operation (the property name is the DataLoader name
unless `dataLoader(name = …)` sets one). `load` is suspend and must run inside the resolver.
Graphix starts that resolver undispatched so sibling `load`s share one batch, and dispatches
when the engine is idle so a `load` after other suspend work still completes.

The batch lambda is `(List<K>) -> Map<K, V>` or `(List<K>, DataFetchingEnvironment) -> Map<K, V>`.
The DFE is this field's: `graphQlContext`, source, arguments. One DFE represents the whole
batch — GraphQLContext is shared; a field's source and arguments belong in the key.

`@BatchMapping` may take `@Argument` parameters and this field's `DataFetchingEnvironment`.
A name that collides with a property fails schema build; `@GraphQLIgnore` the property if
the resolver should own the field.

A `@SubscriptionMapping` function returns a stream of `T`, not `T` itself. Collect it with
`Graphix.subscribe`, which is a `Flow<GraphixResult>` — one item per event, cancelled when the
collector is. `execute` on a subscription document throws rather than serialising graphql-java's
`Publisher` as a single JSON object.

The data fetcher is not part of the public API. A resolver is a function on an instance Graphix
already holds. What it can see is exactly four things:

| Need | Where it comes from |
| --- | --- |
| A Spring bean, a store, a client | The constructor (or property) of the query/mutation/type class. The data fetcher calls *that* instance |
| Arguments from the GraphQL document | `@Argument` parameters, bound from `variables` / literals |
| Who is calling, the HTTP request, anything per request | A parameter of that type, registered with `contextParameter(...)` and filled from `execute`'s `context` map — see [Framework parameters](#framework-parameters) |

A Spring `OrderService` is not GraphQL context. It is injected when Spring builds the
`@GraphQLController`, and Graphix keeps that bean:

```kotlin
@GraphQLController
class OrderMutations(
    private val orders: OrderService,
) {
    @MutationMapping
    suspend fun placeOrder(@Argument input: PlaceOrderInput): Order = orders.place(input)
}

@Serializable
data class PlaceOrderInput(
    val sku: String,
    val quantity: Int = 1,
)
```

Per-request values do not exist at `@Bean` time. They go on `execute`, keyed by `KClass`, and a
missing key fails the field with `GraphixException`:

```kotlin
data class Caller(val userId: String)

Graphix {
    contextParameter(Caller::class)      // the application registers its own context type
    resolvers(OrderMutations(orders))
}

@MutationMapping
suspend fun placeOrder(
    @Argument input: PlaceOrderInput,
    caller: Caller,
): Order = orders.place(input, caller.userId)

graphix.execute(
    GraphixRequest(query),
    context = mapOf(Caller::class to Caller(userId)),
)
```

Both HTTP integrations fill that map on every operation — see
[Framework parameters](#framework-parameters) — so a `Caller` is normally put there by an
[interceptor](#interceptors) rather than by whoever calls `execute`.

### Framework parameters

**There is one rule.** A resolver's value parameter is the parent source, an `@Argument`, or a type
the builder registered — and the last kind is read from the operation context by `KClass`. No
annotation marks a context parameter; the type does.

`DataFetchingEnvironment` is not a constructor argument and not a GraphQL argument. A mapping that
needs this field's source, arguments or DataLoader takes `dfe: DataFetchingEnvironment` by type.
Three more types work the same way:

| Type | Registered by | What it is |
| --- | --- | --- |
| `graphql.GraphQLContext` | Nothing — it is graphql-java's own | The whole context bag, when a resolver would rather read several keys than list them |
| `io.ktor.server.application.ApplicationCall` | `stx-graphix-ktor`, at install | The call the operation arrived on |
| `org.springframework.web.server.ServerWebExchange` | `stx-graphix-spring`, in the auto-configuration | The exchange the operation arrived on |

```kotlin
@QueryMapping
fun me(call: ApplicationCall): String = call.request.headers["X-User"] ?: "anonymous"
```

The last two are **registered, not guessed**. The core names no framework type; an integration
declares its own with `contextParameter(...)` when it builds the engine, and puts the value in the
context on every route:

```kotlin
Graphix {
    contextParameter(ServerWebExchange::class)   // what stx-graphix-spring does for you
    resolvers(ProductQueries(store))
}
```

A type nobody registered is still an error at schema build, naming the parameter — the same error a
forgotten `@Argument` has always produced. That is what keeps a typo from being silently treated as
context, and it is why a third stack needs one call rather than a change to Graphix.

**Whoever fills the context registers the type.** The core registers `GraphqlWsInit`, which it puts
on every graphql-ws operation (*[The connection_init payload](graphix-serving.md#the-connection_init-payload)*, which
also has the reason a resolver should usually not take one); `stx-graphix-ktor` registers
`ApplicationCall`; `stx-graphix-spring`
registers `ServerWebExchange`; an application registers its own `Caller`, because it is its own
interceptor that puts one there.

A framework parameter counts as *supplied*, which matters for `@SchemaMapping`: the parent source is
the first parameter Graphix does not supply itself, so `fun reviews(call: ApplicationCall, product:
Product)` still resolves `Product` as the parent.

## Interceptors

A `GraphixInterceptor` wraps one operation. It can rewrite the request, contribute to the operation
context, refuse the operation without calling the engine, or shape what comes back:

```kotlin
Graphix {
    resolvers(ProductQueries(store))
    intercept {
        val token = call.request.headers["Authorization"]
            ?: return@intercept flowOf(GraphixResult(null, listOf(GraphixError("unauthenticated"))))
        put(Caller(verify(token)))
        proceed()
    }
}
```

`GraphixChain` is the receiver: `request` is a `var`, `put`/`get` reach the operation context by
`KClass`, and `proceed()` is the rest of the chain ending at the engine. Interceptors run in
registration order, outermost first.

**`proceed()` returns a `Flow<GraphixResult>` for every operation kind** — one element for a query or
a mutation, N for a subscription. That is deliberate: `proceed().map { … }` shapes a single response
and every subscription event with the same line, so there is no second hook to write for streaming,
and one interceptor reads the same over POST, over SSE and over graphql-ws.

The chain runs **once per operation, not once per connection**. On a graphql-ws socket that means
every `subscribe` frame, which is what lets a credential that expires mid-socket be seen to have
expired — the client's `connection_init` payload is in the context as `GraphqlWsInit`, re-read each
time rather than frozen at the handshake. *[The connection_init
payload](graphix-serving.md#the-connection_init-payload)* has its shape.

An interceptor may not unmake the operation: the engine's own reserved keys are written after the
chain's, so putting something under `OperationScope` has no effect. The per-operation
`GraphixMessages` and `GraphixLimits` overrides are honoured.

Registration differs by stack:

| Stack | How |
| --- | --- |
| Core | `intercept { }` in the `Graphix { }` builder |
| Ktor | `intercept { }` in `install(GraphQL) { }` |
| Spring | A `GraphixInterceptor` `@Bean`; `@Order` decides which is outermost |
| Koin | A `GraphixInterceptor` single, collected by `fromKoin()` — see [From a Koin container](#from-a-koin-container) |

Each integration also ships accessors for the type it owns — `GraphixChain.call`,
`DataFetchingEnvironment.call` and `GraphQLContext.call` in Ktor, and the `exchange` twins in
Spring — so a field directive or a resolver holding a DFE reaches the request without going through
`graphQlContext.get(...)` by hand.

## Exception handlers

A resolver that throws becomes an error in `errors[]` carrying the exception's own message. That is
rarely what a client should read, and never what it should be *classified* as. An
`errors { }` block is where an application says otherwise:

```kotlin
Graphix {
    resolvers(ProductQueries(store))
    errors {
        on<ProductNotFound> { failure -> error.withMessage("No product ${failure.id}").withErrorType(NOT_FOUND) }
        on<AccessDenied> { error.withErrorType(FORBIDDEN) }
        fallback { error.withMessage("Internal error").withErrorType(INTERNAL_ERROR) }
    }
}
```

`error` is the error **as it already stands** — message from the unwrapped exception, `path` and
`locations` already filled from the failing field — so a handler that only classifies restates
nothing. It is a `data class`, and the `withX` extensions are names for its copies:
`withMessage`, `withErrorType` (a `GraphixErrorType` or any string), `withExtension`,
`withExtensions`, `withPath`, `withLocations`. This is Spring GraphQL's
`@GraphQlExceptionHandler(GraphqlErrorBuilder<?>, Exception)` slot, in immutable form.

Blocks are `suspend` and run on the operation's scope, so a handler that has to ask a service what
the error should say can do it without blocking the engine thread. The receiver also carries
`environment` (the `DataFetchingEnvironment`, `null` outside a field), `get<T>()` for the operation
context, and `message(key, args)` for the operation's locale and `GraphixMessages` — a
handler's message is localised the way a coercion error already is.

**Returning `null` means *not mine*.** The next handler is asked. **Nothing changes until a handler
claims it:** an exception nobody claims keeps exactly the answer it would have had, because
registering a handler for one exception type is not a decision about every other one.
`fallback { }` is how an application says it wants all of them, and it is asked last whatever the
registration order — it has to be, since handlers arriving from a container have no order anyone
controls.

**Order decides**, the way it already does for interceptors: each handler is asked in turn and the
first to answer wins. A handler for a specific exception goes in *before* a broader one that would
also claim it — `on<SmallBoom>` before `on<Boom>`, not after.

Written as a class rather than a block, a handler is a `GraphixExceptionHandler` — one interface,
one function, branching on the exception itself:

```kotlin
@Singleton                                   // or a Spring @Component / @Bean
class CatalogErrors : GraphixExceptionHandler {
    override suspend fun GraphixErrorScope.handle(failure: Throwable): GraphixError? =
        when (failure) {
            is ProductNotFound -> error.withMessage("No product ${failure.id}").withErrorType(NOT_FOUND)
            is AccessDenied -> error.withErrorType(FORBIDDEN)
            else -> null
        }
}
```

There is one mechanism, not two: `on<T> { }` is sugar over a handler that declines anything but `T`.
A single function is what a container can be asked for — `getAll<T>()`, `ObjectProvider<T>` — and it
is what lets the same handler be registered by hand, from a block, or found as a bean.

| Stack | How |
| --- | --- |
| Core | `errors { }` in the `Graphix { }` builder, or `exceptionHandler(…)` |
| Ktor | `errors { }` / `exceptionHandler(…)` in `install(GraphQL) { }` — refused beside `instance`, since handlers live on the engine |
| Spring | A `GraphixExceptionHandler` `@Bean`; `@Order` decides which is asked first |
| Koin | A `GraphixExceptionHandler` single, collected by `fromKoin()` |

`GraphixErrorType` is the vocabulary Spring GraphQL uses — `BAD_REQUEST`, `UNAUTHORIZED`,
`FORBIDDEN`, `NOT_FOUND`, `INTERNAL_ERROR` — and `withErrorType(String)` takes anything else, since
graphql-java's own classifications (`ValidationError`, `DataFetchingException`) are strings too. It
reaches a client through `extensions.classification`, which is the only place the GraphQL spec has
for it, and only when the key is not already there — the rule graphql-java's own serializer follows.

### Where a throw is caught

Three places, because a throw does not always have a field to fail:

| Where | What reaches the handler |
| --- | --- |
| A resolver, a type field, a `@BatchMapping`, a subscription event | Yes — this is graphql-java's `DataFetcherExceptionHandler` seat. Both a plain function's `InvocationTargetException` and a `suspend` one's `CompletionException` are unwrapped first |
| An interceptor, or `subscribe` before the flow is returned | Yes — there is no `DataFetchingEnvironment`, so `environment` is `null` and the operation context is read from the request. An unclaimed throw is rethrown, exactly as before |
| A subscription `Flow` throwing **mid-stream** | Yes — the failing event becomes a `GraphixResult` with `errors[]`. Unclaimed, it still escapes the flow |

`GraphixException` is deliberately never offered: it means the operation could not be submitted — a
subscription sent to `execute`, a document that never reached the engine — which is the caller's bug,
not a client's error, and hiding it under a `fallback { }` would keep it from the only person who
can fix it. `CancellationException` is skipped for the same reason it always is.

> **One trap.** graphql-java applies `defaultDataFetcherExceptionHandler` only to execution
> strategies left null at build, so `engine { queryExecutionStrategy(…) }` silently defeats the first
> seat. Nothing warns. An explicit `engine { defaultDataFetcherExceptionHandler(…) }` wins on
> purpose — engine customizers run last, and writing one is a choice. `ExceptionHandlerTest` pins
> both.

## From a Koin container

`stx-graphix-koin` builds the whole schema from what Koin holds, in one call:

```kotlin
@Singleton
class ProductQueries(private val store: Store) : GraphixResolver {
    @QueryMapping
    suspend fun products(): List<Product> = store.all()
}

Graphix { fromKoin() }          // or, under Ktor: install(GraphQL) { schema { fromKoin() } }
```

It collects every `GraphixResolver`, `GraphQLScalarType`, `GraphixDirective`, `GraphixCustomizer`,
`GraphixInterceptor`, `GraphQLEngineCustomizer` and `GraphixExceptionHandler` single. `fromKoin()` is a `GraphixBuilder`
extension, so it is the same call under Ktor, under Spring, and in a plain `Graphix { }`.

The same boundary as Spring's applies: `getAll<T>()` enumerates the whole container, so a single a
**dependency** contributes is collected like a local one — but only once its module is loaded, which
means the library ships a `@Module` the application names in `modules(…)` or reaches with
`@ComponentScan("com.acme.billing")`. Collection cannot register what the container was never given.

**Only resolvers need the marker.** The other six are already types, so the type *is* the marker
and a `@Singleton` binding it is found as it stands. A resolver is an ordinary class holding
`@QueryMapping` functions, with nothing in common with the next one, and Koin — unlike Spring —
cannot be queried by annotation: `getAll<T>()` answers *"every single bound to T"*, which is a
question only a type can ask. Hence `GraphixResolver`, implemented rather than annotated.

> A meta-annotation would be nicer — `@Singleton annotation class GraphQLController`, one mark
> rather than two — and it does **not** work. Koin's compiler plugin matches direct annotations
> only, so a meta-annotated class compiles and is then silently absent from the container. Measured
> on Koin 4.2.2 / koin-compiler-plugin 1.1.0. Spring's `@GraphQLController` behaves differently
> because Spring reads annotation metadata at runtime, where meta-annotations are visible.
