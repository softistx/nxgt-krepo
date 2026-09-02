# What a stx-graphix schema may say

The annotation and scalar vocabulary. This is the half of `libs/stx-graphix` that gains an entry
every phase — a new annotation, a new scalar, a new mapping rule — so it lives here rather than
in the module README, which answers *why the library is shaped this way*.

`libs/stx-graphix/stx-graphix/README.md` has the reasoning.
[The last section](#the-whole-thing) is a whole schema and the class that answers it.

## Roots

| Annotation | Where | Becomes |
| --- | --- | --- |
| `@QueryMapping` | function on a class passed to `query(...)` | field on `Query` |
| `@MutationMapping` | function on a class passed to `mutation(...)` | field on `Mutation` |
| `@SubscriptionMapping` | function on a class passed to `subscription(...)` | field on `Subscription` |
| `@SchemaMapping` | function on a class passed to `type(...)` | extra field on the parent type |
| `@BatchMapping` | function on a class passed to `type(...)` | extra field, DataLoader — no SchemaMapping on the same field |

The GraphQL field name is `@QueryMapping(name=…)` / `@MutationMapping(name=…)` /
`@SubscriptionMapping(name=…)` if set, otherwise the Kotlin name. `@SchemaMapping(typeName, field)` and `@BatchMapping(typeName, field)` default
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
| `@GraphQLId String` / `Uuid` / `Long` | `ID` |
| `kotlin.time.Instant` | `Instant` (ISO-8601 string) |
| `kotlin.uuid.Uuid` | `Uuid` (canonical string) |
| `List<T>` | `[T]` |
| `T?` | nullable `T` |
| enum class | GraphQL enum, constant names from the serializer |
| `sealed` with shared properties | GraphQL `interface` — see *Interfaces and unions* |
| `sealed` with none, or `@GraphQLUnion` | GraphQL `union` |

A type that is not `@Serializable` fails schema build, naming that type.

**A field's name is `@SerialName`**, otherwise the Kotlin property name — the SerialDescriptor is
the type system here, so it owns that name alone. The field still reads the Kotlin property it
renamed: graphql-java's own fetcher would look for the GraphQL name on the object and find nothing,
so a renamed field gets one that knows both.

Nothing else may rename a property, and that is deliberate. An **input** object is decoded straight
from the argument map, keyed by the names the schema advertises, so a schema name that was not the
serial name would decode to a missing field — or, when the property has a Kotlin default, silently
to that default, losing what the caller sent. On an **output** there is no separate wire at all:
kotlinx-serialization never encodes a resolver's return value, the response JSON is built from the
GraphQL data map, and its keys are the field names. So a second renaming annotation could only ever
agree with `@SerialName` or be wrong.

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

A nested `sealed` level under a **union** is Kotlin structure: only the concrete leaves are member
types. Under an **interface** it is a GraphQL interface of its own, because it carries the shared
properties too — `sealed interface Paper : Ticketed` prints as `interface Paper implements Ticketed`,
and a leaf under it declares the whole chain (`type Boarding implements Paper & Ticketed`). GraphQL
does not infer that from `Paper`, so a leaf that named only its nearest level would not be a possible
type of the outermost one.

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
    scalar("Money", kotlinType = Money::class, specifiedBy = "https://example.test/money") {
        serialize { value -> (value as Money).cents.toString() }
        parseValue { input -> Money((input as String).toLong()) }
        parseLiteral { input -> Money((input as graphql.language.StringValue).value!!.toLong()) }
    }
    query(PriceQueries())
}
```

`kotlinType` is how an annotated field of that class becomes this scalar. Without it the
scalar exists on the schema (SDL `scalar Money`, or `additionalType`) but Kotlin fields
still need a serializer. `specifiedBy` is GraphQL's `@specifiedBy` — the URL of the scalar's own
specification, which is what a client generator reads to learn what the string actually holds.
It surfaces as `__type(name: "Money") { specifiedByURL }`.

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

A directive wraps a **fetcher**, so the SDL locations it can honour are the ones that have
fetchers behind them:

| Location | What the wrapper does |
| --- | --- |
| `FIELD_DEFINITION` | Wraps that one field |
| `OBJECT` | Wraps **every** field of the type |
| `INTERFACE` | Wraps every field of the interface, on the interface's own coordinates |
| `ARGUMENT_DEFINITION`, `INPUT_FIELD_DEFINITION` | **Not supported** — these transform an input value rather than wrap a fetcher |

```graphql
directive @audit on FIELD_DEFINITION | OBJECT | INTERFACE

type Ticket @audit {
  id: String!
  title: String!
}
```

One `fieldDirective("audit") { … }` then runs around `id` and `title` alike. The wrapper sees
which field it is on through `environment.field`, and the directive's own arguments through
`arguments` — on an `OBJECT` those are the arguments applied to the type, the same values for
every field. `@Directive` on a Kotlin function is `FIELD_DEFINITION` only; a code-first schema
has no type-level equivalent.

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
| `@GraphQLName("foo")` | **class only** | GraphQL type name. A property is renamed with `@SerialName`, an argument with `@Argument(name=…)`, a field with its own mapping annotation — one owner per name |
| `@GraphQLDescription("…")` | same | GraphQL description |
| `@GraphQLIgnore` | property | omitted from the GraphQL type |
| `@Argument("foo")` | parameter | **Required** on every GraphQL argument. [name] defaults to the Kotlin parameter name |
| `@GraphQLContext` | parameter | other types from `execute`'s context map. `DataFetchingEnvironment` is this field **by type** and does not need the annotation |
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
field's `DataFetchingEnvironment` (by type), and `@GraphQLContext` values. An input object's
fields are not arguments either: `CreateProductInput` is the `@Argument`, `name` and `tags`
are its fields (`@SerialName` / `@GraphQLIgnore` still apply). A Kotlin default on an
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
| `__typename`, `__schema`, `__type` | Introspection is on by default; `introspection(false)` turns `__schema`/`__type` off, and `__typename` keeps working |

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
throws becomes an error there; `execute` itself still returns.

A `GraphixError` carries the whole spec shape — `message`, `path`, `locations` (1-based line and
column), `extensions`, and graphql-java's `errorType` classification (`ValidationError`,
`DataFetchingException`, and so on), which is what tells a bad document from a resolver that threw.
`result.extensions` is whatever instrumentation put in the result's, usually empty. Subscription events keep that same
shape, in order (`KEEP_SUBSCRIPTION_EVENTS_ORDERED`).

## HTTP

The JSON envelope is `{ "query", "variables", "operationName", "extensions" }`. The response is
`{ "data", "errors", "extensions" }`, where an error carries `message`, `path`, `locations` and its
own `extensions`. Anything empty is **omitted** rather than sent as `[]` or `{}`. Request
`extensions` are passed through to the operation untouched, where instrumentation can read them. A field error is HTTP **200** with `errors[]`. Malformed JSON, a missing
query, or unparseable GET `variables` is HTTP **400** with `errors[]`.

`GET /graphql?query=...` is for introspection and simple queries. Variables on GET are a JSON
object in the `variables` query parameter. `__schema` and `__type` are on by default — GraphiQL
and Apollo Sandbox POST the standard introspection query to the same path.

Turning them off is one switch, on the engine or through either integration:

| Where | How |
| --- | --- |
| `Graphix { }` | `introspection(false)` |
| Ktor | `install(GraphQL) { introspection = false }` |
| Spring Boot | `stx.graphix.introspection=false` |

A document that then asks for `__schema` or `__type` comes back as a GraphQL error rather than
data; `__typename` is unaffected, and every other field runs as usual. An engine supplied by the
application (Ktor `instance`, or a `Graphix` bean) already decided for itself and ignores the
setting.

### Apollo Sandbox

`GET /sandbox` serves Apollo's embedded sandbox — a query editor with the schema in a side panel,
against this server. **Off by default**: opening a GraphQL endpoint is what installing the plugin
means, opening an HTML page that advertises the schema is not.

| | Ktor | Spring Boot | Default |
| --- | --- | --- | --- |
| Serve it | `sandbox = true` | `stx.graphix.sandbox=true` | `false` |
| Where | `sandboxPath = "/explorer"` | `stx.graphix.sandbox-path` | `/sandbox` |
| Which endpoint | `sandboxEndpoint = "…"` | `stx.graphix.sandbox-endpoint` | empty — resolved in the browser |

Left empty, the page resolves the endpoint itself: `new URL(<the GraphQL path>, window.location.origin)`.
That is what survives a reverse proxy, https, and a container publishing a port other than the one
the server bound — the host and port the *client* reached are the only ones that are right, and only
the browser knows them. Set `sandbox-endpoint` to an absolute URL to point it somewhere else.

Two things it needs. **Introspection**, since that is how it draws the schema: the page still loads
with `introspection(false)`, the schema panel is just empty. And **the CDN** — the page pulls
`embeddable-sandbox.cdn.apollographql.com`, so a strict `Content-Security-Policy` or an air-gapped
network will block it, and the page renders blank with a console error.

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

## The whole thing

Every section above is one annotation or one setting. This is a schema and the class that answers it
— a query, a mutation, a subscription, a union and the batch mapping that keeps a nested list from
being an N+1. It is `examples/graphix-shop`, so it builds and its four specs run on every build.

### The documents are the schema

`resources/graphql/` is scanned, nested folders included, and every file is merged — so the schema is
split the way the domain is rather than the way a scanner would like:

```graphql
# product.graphqls
type Product {
  id: String!
  name: String!
  price: Long!
  reviews: [Review!]!
}

# review.graphqls
type Review {
  id: String!
  body: String!
}

# search.graphqls
"A product or a review — whichever matched the term."
union SearchResult = Product | Review

extend type Query {
  search(term: String!): [SearchResult!]!
}

# schema.graphqls
scalar Long

type Query {
  product(id: String!): Product
  products: [Product!]!
}

type Mutation {
  addProduct(name: String!, price: Long!): Product!
}

type Subscription {
  productAdded: Product!
}
```

`extend type Query` is how a file adds a field to a type another file declared, which is what makes
one file per concern possible without one file listing every root field. `scalar Long` is declared
because a field uses it; the scalar itself is wired automatically, along with `Instant` and `Uuid`.

### One class answers all of it

```kotlin
class Catalog {
    @QueryMapping
    fun product(@Argument id: String): Product? = products.find { it.id == id }

    @QueryMapping
    fun products(): List<Product> = products.toList()

    @QueryMapping
    fun search(@Argument term: String): List<Any> { … }

    @MutationMapping
    fun addProduct(@Argument name: String, @Argument price: Long): Product { … }

    @SubscriptionMapping
    fun productAdded(): Flow<Product> = added

    @BatchMapping
    fun reviews(products: List<Product>): Map<Product, List<Review>> =
        products.associateWith { reviews[it.id].orEmpty() }
}
```

Four things there are the ones worth copying:

- **`@BatchMapping` and not `@SchemaMapping`.** `{ products { reviews { body } } }` resolves
  `reviews` once per product with a field mapping — the N+1, in a GraphQL server rather than in a
  database layer. A batch mapping is handed every parent at once and answers a `Map`, so the whole
  query is two loads regardless of how many products came back.
- **`search` returns `List<Any>`, and that is not a shortcut.** Kotlin has no union type. On the SDL
  path the *document* is the schema, so a resolver's Kotlin return type is never read for shape —
  Graphix resolves each row by its class name, with no type resolver registered. `Product` and
  `Review` are named in the union, so they resolve.
- **`@SubscriptionMapping` returns a `Flow`.** Cancelling the transport cancels it; nothing here has
  to unregister anything.
- **One instance, four roles.** The same `Catalog` is the query root, the mutation root, the
  subscription root and the owner of a type field, because that is where the state is. Nothing
  requires them to be one class, and nothing requires them to be four.

### Wiring it up

```kotlin
fun Application.shop() {
    val catalog = Catalog()
    install(GraphQL) {
        sandbox = true
        schema {
            query(catalog)
            mutation(catalog)
            subscription(catalog)
            type(catalog)
        }
    }
}
```

`/graphql` for the operations, `/sandbox` for the Apollo Sandbox. In Spring the same class is a
`@GraphQLController` bean and `stx.graphix.enabled=true` is the whole of the wiring —
[`docs/spring-configuration.md`](spring-configuration.md) has the keys.

### What a request looks like

```bash
curl -s localhost:8080/graphql -H 'content-type: application/json' \
  -d '{"query":"{ products { name price reviews { body } } }"}'
```

```json
{"data":{"products":[
  {"name":"Mug","price":1200,"reviews":[{"body":"Holds coffee"}]},
  {"name":"Kettle","price":4500,"reviews":[{"body":"Boils fast"}]}
]}}
```

And the union, selected with inline fragments and resolved by class name alone:

```graphql
{ search(term: "co") { __typename ... on Product { name } ... on Review { body } } }
```

```json
{"data":{"search":[{"__typename":"Review","body":"Holds coffee"}]}}
```

A subscription over the same schema is the same document through the socket:
`subscriptions = GraphqlWs` on the plugin, `graphql-transport-ws` on the wire, and a POST of a
subscription document is then a 400 — the socket is the subscription transport, and an HTTP body
that asks for one is asking the wrong endpoint.
