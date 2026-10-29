# What a stx-graphix schema may say

The annotation and scalar vocabulary. This is the half of `libs/api/stx-graphix` that gains an entry
every phase — a new annotation, a new scalar, a new mapping rule — so it lives here rather than
in the module README, which answers *why the library is shaped this way*.

`libs/api/stx-graphix/stx-graphix/README.md` has the reasoning.

This page is the schema: roots, types, scalars and validation. What a resolver may take and do is
[`graphix-resolvers.md`](graphix-resolvers.md); serving the schema (documents, `execute`, HTTP,
SSE, graphql-ws) is [`graphix-serving.md`](graphix-serving.md).

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
JDK `Publisher<T>`; `T` is the GraphQL field type. The same types on a query or a mutation mean a
**list**, not a stream — see *Types*.

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
| `Flow<T>`, `Publisher<T>` (a function return, not a property) | `[T]` — **collected**, not streamed |
| `T?` | nullable `T` |
| enum class | GraphQL enum, constant names from the serializer |
| `sealed` with shared properties | GraphQL `interface` — see *Interfaces and unions* |
| `sealed` with none, or `@GraphQLUnion` | GraphQL `union` |

A type that is not `@Serializable` fails schema build, naming that type.

**A `Flow` means two things, and the annotation decides which.** On `@SubscriptionMapping` it is a
stream of *separate responses*, one per element. Anywhere else — a query, a mutation, a
`@SchemaMapping` type field — it is one list inside a single response: the library collects it, on
the operation's `CoroutineScope`, before graphql-java sees anything. That is the `.toList()` the
resolver would otherwise write, and writing it in the library rather than in the resolver is what
puts the collection under the request's cancellation and inside the seat where a throw becomes an
`errors[]` entry. It is **not** incremental delivery — see [*Documents*](graphix-serving.md#documents) for why there is none.

The reason it exists is that the answer should not depend on the store: `stx-mongo`'s `findAll`
hands a resolver a `Flow` and `stx-jpa`'s a `List`, and a resolver body should not differ for that.
A reactive-streams `Publisher` and a JDK `Flow.Publisher` collect the same way. A
`CompletionStage<Flow<T>>` is refused at schema build naming both — a data fetcher has to hand a
stage back untouched, and nobody writes that signature in Kotlin.

`maxListElements(n)` on the builder bounds how many elements such a field may produce. Unset,
nothing is bounded — a `List` return never was either. It exists because a `Flow` can be *infinite*
where a `List` cannot: without a bound such a field does not fail, it never answers. Past it the
field is a `TooManyElements` in `errors[]`, which an `errors { }` handler can edit like any other.
It bounds emission, not silence: a flow that emits nothing and never completes still hangs, and that
wants a request timeout, which is a different thing.

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

A scalar is one file under `libs/api/stx-graphix/stx-graphix/src/scalar/` holding its `Coercing` and
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
instead, see [From a Koin container](graphix-resolvers.md#from-a-koin-container).
