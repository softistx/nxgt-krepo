# What a stx-graphix schema may say

The annotation and scalar vocabulary. This is the half of `libs/stx-graphix` that gains an entry
every phase — a new annotation, a new scalar, a new mapping rule — so it lives here rather than
in the module README, which answers *why the library is shaped this way*.

`libs/stx-graphix/stx-graphix/README.md` has the reasoning.

## Roots

| Annotation | Where | Becomes |
| --- | --- | --- |
| `@QueryMapping` | function on a class passed to `query(...)` | field on `Query` |
| `@MutationMapping` | function on a class passed to `mutation(...)` | field on `Mutation` |
| `@SubscriptionMapping` | function on a class passed to `subscription(...)` | field on `Subscription` |
| `@SchemaMapping` | function on a class passed to `type(...)` | extra field on the parent type |
| `@BatchMapping` | function on a class passed to `type(...)` | extra field, DataLoader — no SchemaMapping on the same field |

The GraphQL field name is `@QueryMapping(name=…)` / `@MutationMapping(name=…)` /
`@SubscriptionMapping(name=…)` if set, otherwise `@GraphQLName` on the function, otherwise the
Kotlin name. `@SchemaMapping(typeName, field)` and `@BatchMapping(typeName, field)` default
`typeName` to the simple name of the first argument's type and `field` to the function name.

At least one `@QueryMapping` function is required. GraphQL's spec has no schema without a query root.
`@SubscriptionMapping` is optional. The Kotlin return type must be `Flow<T>` or a reactive-streams /
JDK `Publisher<T>`; `T` is the GraphQL field type.

## Types

`@Serializable` data classes become GraphQL object types (output) or input object types (input).
The GraphQL name is `@GraphQLName` on the class, otherwise the Kotlin simple name. If that name
is already an output type, an input object is named `{Name}Input`. A class whose name already
ends in `Input` keeps it.

| Kotlin | GraphQL |
| --- | --- |
| `String` | `String` |
| `Int` | `Int` |
| `Boolean` | `Boolean` |
| `Float`, `Double` | `Float` |
| `Long` | `Long` (custom scalar; GraphQL `Int` is 32-bit) |
| `kotlin.time.Instant` | `Instant` (ISO-8601 string) |
| `kotlin.uuid.Uuid` | `Uuid` (canonical string) |
| `List<T>` | `[T]` |
| `T?` | nullable `T` |
| enum class | GraphQL enum, constant names from the serializer |
| `sealed` with shared properties | GraphQL `interface` — see *Interfaces and unions* |
| `sealed` with none, or `@GraphQLUnion` | GraphQL `union` |

A type that is not `@Serializable` fails schema build, naming that type.

`Map` is not a GraphQL type. A `sealed` hierarchy is — see the next section. An `abstract` or
`open` polymorphic type registered in a `SerializersModule` is not: GraphQL needs a closed set of
possible types, and only a sealed hierarchy has one.

## Interfaces and unions

A `sealed` hierarchy is the abstract type. Which one it becomes is the shape of the Kotlin code,
not a second annotation:

```kotlin
@Serializable
sealed interface Media {              // -> interface Media { id: String!  title: String! }
    val id: String
    val title: String
}

@Serializable data class Film(override val id: String, override val title: String, val minutes: Int) : Media
@Serializable data class Song(override val id: String, override val title: String, val bpm: Int) : Media

@Serializable
sealed interface SearchHit            // -> union SearchHit = BookHit | AuthorHit

@Serializable data class BookHit(val title: String) : SearchHit
@Serializable data class AuthorHit(val name: String) : SearchHit
```

A sealed type that declares properties every subclass carries is an `interface`; one that declares
none can only be a `union`, since a GraphQL interface needs at least one field. `@GraphQLUnion` on
the sealed type forces the union direction when it does have shared properties. There is no
annotation for the other direction.

A nested `sealed` level is Kotlin structure: only the concrete leaves are member types.

**Type resolution** is the runtime value's own name — `@GraphQLName` on its class, otherwise the
Kotlin simple name — because the object *is* the Kotlin instance. Nothing round-trips through
kotlinx.serialization on the way out, so the discriminator the serializer would have written is not
there to read. Nothing is registered, and this works identically on an SDL schema: a document may
declare `union` and `interface` and Graphix wires the resolver for each.

A value it cannot place is a **GraphQL error on that field**, in `errors[]` — not a thrown
exception. Override the rule per type when the class name is not the GraphQL name:

```kotlin
Graphix {
    typeResolver("SearchResult") { value -> if (value is Row) "Product" else "Review" }
    query(SearchQueries(store))
}
```

`@SchemaMapping` and `@BatchMapping` may target an **interface**: the field is added to the
interface and to every implementor, and the resolver is registered at each implementor's
coordinates — a data fetcher is never inherited down an interface. A mapping declared on one
implementor for the same field wins over the inherited one. On a **union** it is a schema-build
failure: a GraphQL union has no fields.

Two things a sealed hierarchy may not do. It cannot be an **input**: GraphQL has no input unions,
so take a discriminator argument and one input object per case. And a member type needs at least
one field, so a `data object` member fails schema build naming the Kotlin object.

On the **SDL path** a resolver's Kotlin return type is never read — the document is the schema — so
a union field is written `List<Any>`, which is the only way to say it in Kotlin. On the annotation
path the same field is a sealed hierarchy.

## Custom scalars and field directives

A scalar is declared on `GraphixBuilder` — lambdas run with the operation
`GraphQLContext` as receiver, the same bag `@GraphQLContext` reads:

```kotlin
data class Money(val cents: Long)

Graphix {
    scalar("Money", kotlinType = Money::class) {
        serialize { value -> (value as Money).cents.toString() }
        parseValue { input -> Money((input as String).toLong()) }
        parseLiteral { input -> Money((input as graphql.language.StringValue).value!!.toLong()) }
    }
    query(PriceQueries())
}
```

`kotlinType` is how an annotated field of that class becomes this scalar. Without it the
scalar exists on the schema (SDL `scalar Money`, or `additionalType`) but Kotlin fields
still need a serializer.

A **field directive** wraps the original fetcher. `proceed()` is suspend; the wrapper
sees `environment` and `graphQlContext`:

```kotlin
Graphix {
    fieldDirective("uppercase") {
        val value = proceed()
        (value as? String)?.uppercase() ?: value
    }
    query(UpperQueries())
}

class UpperQueries {
    @QueryMapping
    @Directive("uppercase")
    fun hello(): String = "world"
}
```

On an SDL schema, `@uppercase` on the field is enough — `SchemaDirectiveWiring` is
registered under that name. `@Directive` is the annotation-schema equivalent.

Spring collects `GraphQLScalarType`, `GraphixDirective`, `GraphixCustomizer` and
`GraphQLEngineCustomizer` beans the way it collects `@GraphQLController`.
`GraphixCustomizer` is `fun GraphixBuilder.customize()`; `GraphQLEngineCustomizer` is
`fun GraphQL.Builder.customize()` (instrumentation, execution strategy).

## Validation

graphql-java 26 enforces query complexity during validation (`maxDepth` 100, `maxFields` 100_000
unless told otherwise). Graphix surfaces that as a builder block, and as a per-operation value
in `execute`'s context map — the same bag the `CoroutineScope` already lives in, not a process-wide
`setDefaultLimits`.

```kotlin
val graphql = Graphix {
    query(ProductQueries(store))
    validation {
        maxDepth = 8
        maxFields = 500
        field("/createProduct") {
            val name = argument("name") as? String
            "name is too long".takeIf { name != null && name.length > 80 }
        }
    }
}

graphql.execute(
    GraphixRequest("{ products { name } }"),
    context = mapOf(GraphixLimits::class to GraphixLimits(maxDepth = 3)),
)
```

`none()` turns complexity checking off. Field rules see already-coerced arguments and must not
suspend: graphql-java's field-validation hook is not a coroutine. A returned string is the
error; `null` passes. A `GraphixLimits` in the context map **replaces** the engine's limits (both
axes), it does not patch one of them.

Ktor: `customize { }` / `engine { }` on `install(GraphQL)`, and `fromDi = true` pulls the
same types from Ktor DI (`provide<GraphixCustomizer> { … }`).

## Fields and arguments

| Annotation | Where | Effect |
| --- | --- | --- |
| `@GraphQLName("foo")` | class, function, property, parameter | GraphQL name |
| `@GraphQLDescription("…")` | same | GraphQL description |
| `@GraphQLIgnore` | property | omitted from the GraphQL type |
| `@Argument("foo")` | parameter | **Required** on every GraphQL argument. [name] defaults to the Kotlin parameter name |
| `@GraphQLContext` | parameter | other types from `execute`'s context map. `DataFetchingEnvironment` is this field **by type** and does not need the annotation |
| `@Directive("name")` | mapping function | wraps the field with the `fieldDirective("name")` registered on the builder |

`@Argument` is required on every GraphQL argument — a resolver parameter. Unmarked parameters
are not arguments: the parent source (`SchemaMapping` / `BatchMapping` first parameter), this
field's `DataFetchingEnvironment` (by type), and `@GraphQLContext` values. An input object's
fields are not arguments either: `CreateProductInput` is the `@Argument`, `name` and `tags`
are its fields (`@GraphQLName` / `@GraphQLIgnore` still apply). A Kotlin default on an
`@Argument` parameter, or on an input-object property, is optional GraphQL.

Nested object fields are the `@Serializable` properties already in memory. Extra fields that need
I/O are `@SchemaMapping` or `@BatchMapping` on an instance passed to `type(...)`. **A given
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
already holds. What it can see is exactly three things:

| Need | Where it comes from |
| --- | --- |
| A Spring bean, a store, a client | The constructor (or property) of the query/mutation/type class. The data fetcher calls *that* instance |
| Arguments from the GraphQL document | `@Argument` parameters, bound from `variables` / literals |
| Who is calling, the locale, anything per request | `@GraphQLContext` on a parameter, filled from `execute`'s `context` map |

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

@MutationMapping
suspend fun placeOrder(
    @Argument input: PlaceOrderInput,
    @GraphQLContext caller: Caller,
): Order = orders.place(input, caller.userId)

graphix.execute(
    GraphixRequest(query),
    context = mapOf(Caller::class to Caller(userId)),
)
```

The HTTP plugins (Ktor and Spring) currently put only the operation `CoroutineScope` in that map,
so that `suspend` resolvers run. They do not yet forward `ApplicationCall`, `ServerWebExchange` or
Spring Security. Until they do, a per-request `Caller` has to be passed to `execute` by whoever
owns the HTTP call — or the resolver reads it some other way.

`DataFetchingEnvironment` is not a constructor argument and not a GraphQL argument. A mapping
that needs this field's source, arguments or DataLoader takes `dfe: DataFetchingEnvironment`
by type — no `@GraphQLContext`.

## Documents

What the *query* may say is graphql-java's, not Graphix's: nothing below needs wiring, an
annotation or a builder call, and `ConformanceTest` is what proves it.

| In a document | Notes |
| --- | --- |
| `@skip(if:)` / `@include(if:)` | The two spec directives. A skipped field is absent from `data`, not null |
| Named fragments, inline fragments | `fragment f on Product { … }`, `... on Product { … }` |
| Aliases | Two aliases of one field with different arguments are two independent fields — and, for `@BatchMapping`, two DataLoader keys |
| Variables, with their own defaults | `query Q($n: String = "ada")`. A variable explicitly `null` on an optional argument falls through to the Kotlin default |
| `operationName` | Which operation runs when the document holds more than one |
| `__typename`, `__schema`, `__type` | Introspection is on and has no off switch yet |

**Not supported.** `@defer` and `@stream` are `@ExperimentalApi` in graphql-java 26: they need
incremental support switched on, an `IncrementalExecutionResult` path through `execute`, and
`multipart/mixed` on both HTTP plugins. Automatic persisted queries, the multipart upload spec and
Apollo Federation are outside graphql-java core and are not wrapped here.

## Execute

```kotlin
val result = graphix.execute(
    GraphixRequest(query = query, variables = mapOf("id" to "p1")),
    context = mapOf(Caller::class to caller),
)

graphix.subscribe(GraphixRequest("subscription { productAdded { name } }"))
    .collect { event -> /* one GraphixResult per event */ }
```

`result.data` is the GraphQL data map. `result.errors` is the GraphQL error list. A resolver that
throws becomes an error there; `execute` itself still returns. Subscription events keep that same
shape, in order (`KEEP_SUBSCRIPTION_EVENTS_ORDERED`).

## HTTP

The JSON envelope is `{ "query", "variables", "operationName" }`. The response is
`{ "data", "errors" }`. A field error is HTTP **200** with `errors[]`. Malformed JSON, a missing
query, or unparseable GET `variables` is HTTP **400** with `errors[]`.

`GET /graphql?query=...` is for introspection and simple queries. Variables on GET are a JSON
object in the `variables` query parameter. `__schema` and `__type` are on by default — GraphiQL
and Apollo Sandbox POST the standard introspection query to the same path.

A **subscription** is one of two protocols, configurable, default `sse`:

| Protocol | Transport | Config |
| --- | --- | --- |
| `sse` | `text/event-stream` on POST, one `data: {json}` frame per event | Ktor `subscriptions = Sse`; Spring `stx.graphix.subscriptions=sse` |
| `graphql-ws` | WebSocket on the same path, sub-protocol `graphql-transport-ws` | Ktor `subscriptions = GraphqlWs`; Spring `stx.graphix.subscriptions=graphql-ws` |

SSE keeps the `{ "data", "errors" }` envelope. Cancelling the HTTP client cancels the `Flow`.
`graphql-ws` speaks `connection_init` / `connection_ack`, `subscribe` / `next` / `complete`,
and `ping` / `pong`. HTTP POST of a subscription document is then 400 — the socket is the
subscription transport. Queries and mutations stay POST/GET, and also run as a single `next`
over the socket.

**Ktor** — `install(GraphQL)` in `stx-graphix-ktor`, path configurable, default `/graphql`.

**Spring Boot** — `stx.graphix.enabled=true` in `stx-graphix-spring`. Beans annotated
`@GraphQLController` become query/mutation/subscription roots. An application's own `Graphix`
bean wins.

## Schema documents

By default Graphix scans `classpath:graphql/` the way Spring GraphQL does: every
`.graphqls` and `.gqls` file under that directory, nested folders included, is parsed and
**merged** (`extend type Query` is how a file adds fields to a type another file named).
`union` and `interface` declarations need no wiring either: Graphix registers a type resolver for
each, and `typeResolver(name) { }` overrides one.
The documents are the GraphQL schema; `@QueryMapping` / `@SchemaMapping` / `@BatchMapping`
are DataFetchers on those fields. Custom scalars `Long`, `Instant` and `Uuid` are wired
automatically — declare them in SDL if a field uses them (`scalar Long`).

No files found: the annotated `@Serializable` types remain the schema, as before.

Ktor: `schemaLocations` / `schemaFileExtensions` on `install(GraphQL)`. Spring:
`stx.graphix.schema-locations` and `stx.graphix.schema-file-extensions`. Core:

```kotlin
Graphix {
    schemaLocations("classpath:graphql/", "classpath:extra/")
    query(ProductQueries(store))
}
```
