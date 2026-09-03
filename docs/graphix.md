# What a stx-graphix schema may say

The annotation and scalar vocabulary. This is the half of `libs/stx-graphix` that gains an entry
every phase — a new annotation, a new scalar, a new mapping rule — so it lives here rather than
in the module README, which answers *why the library is shaped this way*.

`libs/stx-graphix/stx-graphix/README.md` has the reasoning.

## Roots

Every class goes in through one call — `resolvers(...)` — and the annotation on each function says
what it becomes:

| Annotation | Becomes |
| --- | --- |
| `@QueryMapping` | field on `Query` |
| `@MutationMapping` | field on `Mutation` |
| `@SubscriptionMapping` | field on `Subscription` |
| `@SchemaMapping` | extra field on the parent type |
| `@BatchMapping` | extra field, DataLoader — no SchemaMapping on the same field |

```kotlin
Graphix {
    resolvers(ProductQueries(store), ProductMutations(store), ProductFields(reviews))
}
```

One class may carry several kinds; it is registered **once** and contributes to each. There is no
`query(...)` / `mutation(...)` pair to keep in step with the annotations, because the annotations
were already the answer. A class carrying no mapping at all is a build failure naming that class.

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
| `Long`, `Short`, `Byte` | `Long`, `Short`, `Byte` (GraphQL `Int` is 32-bit) |
| `java.math.BigDecimal`, `java.math.BigInteger` | `BigDecimal`, `BigInteger` |
| `Char` | `Char` (a one-character string) |
| `@GraphQLId String` / `Uuid` / `Long` | `ID` |
| `kotlin.time.Instant` | `Instant` (ISO-8601 string) |
| `kotlin.time.Duration` | `Duration` (ISO-8601, `PT1H30M`) |
| `kotlinx.datetime.LocalDate` / `LocalTime` / `LocalDateTime` | `LocalDate`, `LocalTime`, `LocalDateTime` |
| `kotlin.uuid.Uuid` | `Uuid` (canonical string) |
| `java.net.URI` | `Url` (absolute) |
| `java.util.Locale` | `Locale` (BCP 47 tag) |
| `JsonElement` | `Json` (any JSON value) |
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
    resolvers(SearchQueries(store))
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

## Built-in scalars

GraphQL's own five are `Int`, `Float`, `String`, `Boolean` and `ID`. Everything else a Kotlin
service has fields of is one of these, all of them in `com.softistx.stx.graphix.messages.scalar.Scalars`:

| Scalar | Kotlin type | Wire form |
| --- | --- | --- |
| `Long`, `Short`, `Byte` | `Long`, `Short`, `Byte` | a JSON number, or a quoted one from a client that cannot hold it |
| `BigInteger` | `java.math.BigInteger` | an integer of any width |
| `BigDecimal` | `java.math.BigDecimal` | an exact decimal — the scalar for money |
| `Char` | `Char` | a one-character string |
| `Instant` | `kotlin.time.Instant` | ISO-8601, `2026-09-02T14:30:05Z` |
| `Duration` | `kotlin.time.Duration` | ISO-8601, `PT1H30M` |
| `LocalDate` | `kotlinx.datetime.LocalDate` | `2026-09-02` |
| `LocalTime` | `kotlinx.datetime.LocalTime` | `14:30:05` |
| `LocalDateTime` | `kotlinx.datetime.LocalDateTime` | `2026-09-02T14:30:05` |
| `Uuid` | `kotlin.uuid.Uuid` | the canonical hyphenated string |
| `Url` | `java.net.URI` | an absolute URL |
| `Locale` | `java.util.Locale` | a BCP 47 tag, `fr-CA` |
| `Json` | `JsonElement` | any JSON value |

A field of one of those types **is** that scalar — nothing to register.

**Every built-in is in the schema by default**, used or not, so a client's code generator sees the
whole vocabulary and a field can be retyped without the schema growing a scalar underneath it.
`builtInScalars(false)` on the builder narrows that to the ones a field actually resolved to, for a
schema whose introspection is a published contract; it is `stx.graphix.built-in-scalars` in Spring
and `builtInScalars` on the Ktor plugin. A scalar the application defined under a built-in's name
is unaffected either way — its own definition wins.

`Url` is a `URI` and not a `URL` because `URL.equals` resolves the host through DNS. `Duration`
travels as `PT1H30M` and not as Kotlin's `1h 30m`, because a wire format is parsed by something
that is not Kotlin. `Json` is for a field that genuinely holds a document — a webhook payload, a
settings blob — and for nothing else: a `Json` field tells a client's code generator nothing.

### Bounded scalars

Eight more say what they will accept: `PositiveInt`, `NegativeInt`, `NonPositiveInt`,
`NonNegativeInt` and the same four over `Float`. A range in the **type** is a range the schema
advertises and the engine enforces before a resolver runs; the same check inside the resolver is a
runtime error the client's generated code never saw.

There is no Kotlin type for "an `Int` above zero", so a bounded scalar is named rather than
inferred — in SDL, where the type is simply used:

```graphql
type Query {
  quantity(value: PositiveInt!): PositiveInt!
}
```

The `scalar PositiveInt` line above it is optional: the built-ins the document did not declare are
declared for it. Under `builtInScalars(false)` it is required, and still works — the wiring is
registered whether or not the scalar is declared.

`scalars(Scalars.PositiveInt)` on the builder is the same registration for a schema that has no
documents, and is what `builtInScalars(false)` leaves you with there.

The resolver behind a bounded field takes a plain `Int` or `Double`: the range was already checked.

### Adding one

A scalar is one file under `libs/stx-graphix/stx-graphix/src/scalar/` holding its `Coercing` and
its `GraphQLScalarType`, and one line in `BuiltInScalars`. Nothing else enumerates them — the type
lookup, the SDL wiring and the schema's additional types all read that list. Most of them are two
lambdas over `StringCoercing` or a width over `IntegralCoercing`.

## Coercion errors and their language

A coercion error is the one message this library produces that reaches an API **client**, so it is
looked up by key and locale rather than written in English at the throw site.

`GraphixRequest.locale` is the operation's language. Both HTTP integrations negotiate it from
`Accept-Language` — including the WebSocket, whose handshake settles it once for the socket — and
`acceptedLocale(header)` is that negotiation on its own. Unset, graphql-java falls back to the
**JVM's** default locale, which is the host's environment deciding what language a client is
answered in.

The text comes from `GraphixMessages`. The default is the catalogues in this jar, English and
French; a language they do not ship is a `stx/graphix/messages_<locale>.properties` on the
application's own classpath, and the lookup walks `fr-CA` → `fr` → base **per key**. Replacing the
source outright is one lambda, which is where `stx-i18n` goes:

```kotlin
Graphix {
    messages { locale, key, args -> catalog.forLocale(locale).translate(key, args) }
    resolvers(ProductQueries(store))
}
```

The keys are generic, and that is deliberate: `{scalar}` carries which scalar failed, so a new
scalar needs no new key and no new translation.

| Key | Arguments |
| --- | --- |
| `stx.graphix.messages.scalar.serialize` | `scalar`, `actual` |
| `stx.graphix.messages.scalar.parseValue` | `scalar`, `actual` |
| `stx.graphix.messages.scalar.parseLiteral` | `scalar`, `expected`, `actual` |
| `stx.graphix.messages.scalar.parse` | `scalar`, `value` |
| `stx.graphix.messages.scalar.parseReason` | `scalar`, `value`, `reason` |
| `stx.graphix.messages.scalar.range` | `scalar`, `constraint`, `value` |
| `stx.graphix.messages.literal.string` / `.int` / `.float` / `.boolean` / `.enum` / `.object` / `.list` / `.null` | — |
| `stx.graphix.messages.range.positive` / `.negative` / `.nonPositive` / `.nonNegative` | — |
| `stx.graphix.messages.range.between` | `min`, `max` |

`MessageKeys.All` is that list in code, and `ScalarMessageTest` asserts both bundled catalogues
answer every one of them.

**One thing a replaced source does not reach**, and it is graphql-java's doing: a **literal written
in the document** is coerced during *validation*, where `ValidationContext` builds a
`GraphQLContext` of its own carrying the locale and nothing else. Those errors come from the
bundled catalogue — in the right language, because the locale does survive. A variable's value and
a resolver's result are coerced during *execution* and see the declared source. Adding a language
has no such split: the bundled catalogue answers in both phases.

## Custom scalars and field directives

A scalar is declared on `GraphixBuilder` — lambdas run with the operation
`GraphQLContext` as receiver, the same bag a context parameter reads:

```kotlin
data class Money(val cents: Long)

Graphix {
    scalar("Money", kotlinType = Money::class, specifiedBy = "https://example.test/money") {
        serialize { value -> (value as Money).cents.toString() }
        parseValue { input -> Money((input as String).toLong()) }
        parseLiteral { input -> Money((input as graphql.language.StringValue).value!!.toLong()) }
    }
    resolvers(PriceQueries())
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
    resolvers(UpperQueries())
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

Spring collects `GraphQLScalarType`, `GraphixDirective`, `GraphixCustomizer`, `GraphixInterceptor`
and `GraphQLEngineCustomizer` beans the way it collects `@GraphQLController` — as
`ObjectProvider.orderedStream()`, so `@Order` decides which interceptor is outermost.
`GraphixCustomizer` is `fun GraphixBuilder.customize()`; `GraphQLEngineCustomizer` is
`fun GraphQL.Builder.customize()` (instrumentation, execution strategy).

Collection reads the whole bean factory, so a bean a **dependency** contributes is collected exactly
like a local one — neither `ObjectProvider` nor `getBeansWithAnnotation` knows which jar a class came
from. Registering it is the application's problem, not this module's: Spring Boot's component scan
starts at the `@SpringBootApplication` package, so a library's `@GraphQLController` or interceptor in
another package needs the library's own auto-configuration
(`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`), an explicit
`@ComponentScan`/`@Import`, or an `@Bean`. A bean that does not exist cannot be collected.

## Validation

graphql-java 26 enforces query complexity during validation (`maxDepth` 100, `maxFields` 100_000
unless told otherwise). Graphix surfaces that as a builder block, and as a per-operation value
in `execute`'s context map — the same bag the `CoroutineScope` already lives in, not a process-wide
`setDefaultLimits`.

```kotlin
val graphql = Graphix {
    resolvers(ProductQueries(store))
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

Ktor: `customize { }` / `engine { }` on `install(GraphQL)`. To take them from a container
instead, see [From a Koin container](#from-a-koin-container).

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
on every graphql-ws operation; `stx-graphix-ktor` registers `ApplicationCall`; `stx-graphix-spring`
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
time rather than frozen at the handshake.

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
`GraphixInterceptor` and `GraphQLEngineCustomizer` single. `fromKoin()` is a `GraphixBuilder`
extension, so it is the same call under Ktor, under Spring, and in a plain `Graphix { }`.

The same boundary as Spring's applies: `getAll<T>()` enumerates the whole container, so a single a
**dependency** contributes is collected like a local one — but only once its module is loaded, which
means the library ships a `@Module` the application names in `modules(…)` or reaches with
`@ComponentScan("com.acme.billing")`. Collection cannot register what the container was never given.

**Only resolvers need the marker.** The other five are already types, so the type *is* the marker
and a `@Singleton` binding it is found as it stands. A resolver is an ordinary class holding
`@QueryMapping` functions, with nothing in common with the next one, and Koin — unlike Spring —
cannot be queried by annotation: `getAll<T>()` answers *"every single bound to T"*, which is a
question only a type can ask. Hence `GraphixResolver`, implemented rather than annotated.

> A meta-annotation would be nicer — `@Singleton annotation class GraphQLController`, one mark
> rather than two — and it does **not** work. Koin's compiler plugin matches direct annotations
> only, so a meta-annotated class compiles and is then silently absent from the container. Measured
> on Koin 4.2.2 / koin-compiler-plugin 1.1.0. Spring's `@GraphQLController` behaves differently
> because Spring reads annotation metadata at runtime, where meta-annotations are visible.

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
are DataFetchers on those fields. Every built-in scalar is declared and wired
automatically, so a document may use `LocalDate` or `PositiveInt` without a `scalar` line of its
own. A scalar the document or the application defines itself under a built-in's name keeps its own
meaning: the built-in of that name is dropped rather than colliding with it.

No files found: the annotated `@Serializable` types remain the schema, as before.

Ktor: `schemaLocations` / `schemaFileExtensions` on `install(GraphQL)`. Spring:
`stx.graphix.schema-locations` and `stx.graphix.schema-file-extensions`. Core:

```kotlin
Graphix {
    schemaLocations("classpath:graphql/", "classpath:extra/")
    resolvers(ProductQueries(store))
}
```
