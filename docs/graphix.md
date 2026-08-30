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

A type that is not `@Serializable` fails schema build, naming that type.

`Map` and polymorphic serializers are not GraphQL types yet.

## Fields and arguments

| Annotation | Where | Effect |
| --- | --- | --- |
| `@GraphQLName("foo")` | class, function, property, parameter | GraphQL name |
| `@GraphQLDescription("…")` | same | GraphQL description |
| `@GraphQLIgnore` | property | omitted from the GraphQL type |
| `@Argument("foo")` | parameter | GraphQL argument name (Kotlin name is the default) |
| `@GraphQLContext` | parameter | `DataFetchingEnvironment` is this field; other types come from `execute`'s context map |

A Kotlin default parameter is an optional GraphQL argument. A missing argument uses the default
rather than passing null. A constructor default on an input-object property is an optional GraphQL
input field for the same reason.

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
}
```

`typeName` defaults to the simple name of the first argument's type (`Book`). `field` defaults
to the function name (`author`). `@BatchMapping` takes `List<Parent>` and returns
`Map<Parent, T>` (or `List<T>` in key order). A single parent is N+1; Graphix refuses it.

A `@SchemaMapping` that needs the current field's source or arguments takes
`@GraphQLContext dfe: DataFetchingEnvironment`. That DFE is **this field**, not an entry in
`execute`'s context map.

`@BatchMapping` cannot take GraphQL arguments — close over them, or use `@SchemaMapping`. A
name that collides with a property fails schema build; `@GraphQLIgnore` the property if the
resolver should own the field.

A `@SubscriptionMapping` function returns a stream of `T`, not `T` itself. Collect it with
`Graphix.subscribe`, which is a `Flow<GraphixResult>` — one item per event, cancelled when the
collector is. `execute` on a subscription document throws rather than serialising graphql-java's
`Publisher` as a single JSON object.

The data fetcher is not part of the public API. A resolver is a function on an instance Graphix
already holds. What it can see is exactly three things:

| Need | Where it comes from |
| --- | --- |
| A Spring bean, a store, a client | The constructor (or property) of the query/mutation/type class. The data fetcher calls *that* instance |
| Arguments from the GraphQL document | Function parameters, bound from `variables` / literals |
| Who is calling, the locale, anything per request | `@GraphQLContext` on a parameter, filled from `execute`'s `context` map |

A Spring `OrderService` is not GraphQL context. It is injected when Spring builds the
`@GraphQLController`, and Graphix keeps that bean:

```kotlin
@GraphQLController
class OrderMutations(
    private val orders: OrderService,
) {
    @MutationMapping
    suspend fun placeOrder(input: PlaceOrderInput): Order = orders.place(input)
}
```

Per-request values do not exist at `@Bean` time. They go on `execute`, keyed by `KClass`, and a
missing key fails the field with `GraphixException`:

```kotlin
data class Caller(val userId: String)

@MutationMapping
suspend fun placeOrder(
    input: PlaceOrderInput,
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

`DataFetchingEnvironment` stays inside Graphix. A resolver that needs it is a resolver that has
left the API.

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
object in the `variables` query parameter.

A **subscription** on the same path is `text/event-stream`: one `data: {json}` frame per event,
the same `{ "data", "errors" }` envelope. Cancelling the HTTP client cancels the `Flow`.
WebSocket (`graphql-ws`) is a later slice.

**Ktor** — `install(GraphQL)` in `stx-graphix-ktor`, path configurable, default `/graphql`.

**Spring Boot** — `stx.graphix.enabled=true` in `stx-graphix-spring`. Beans annotated
`@GraphQLController` become query/mutation/subscription roots. An application's own `Graphix`
bean wins.

## What this document does not cover yet

Schema-first SDL, WebSocket (`graphql-ws`). Those land in later slices and get a paragraph here
when they do.
