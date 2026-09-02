# What this generator understands

The reference half of [`libs/stx-openapi-generator`](../libs/stx-openapi-generator/README.md): which parts
of an OpenAPI document are read, what each becomes in Kotlin, and what is deliberately left out.
The module's own README covers the shape of the code and what each client emitter produces; this
file covers the document.

It is kept apart because it is the part that grows. Every phase of work on the generator adds to it
— composition and enums, then inline schemas and vendor extensions, then failures and
authentication — while the module README stays roughly the size it was.

Two rules hold across everything below:

- **Nothing degrades silently.** A document this generator cannot express is a build failure naming
  the schema or the operation, never a quietly weaker type. The exceptions are listed under
  [what it does not handle](#what-it-does-not-handle), and each says what you get instead.
- **A name is derived, unless the document states one.** Every derivation is written down here, and
  every one of them can be overridden with `x-kotlin-name`.

[The last section](#the-whole-thing) is one endpoint travelling the whole way — a split document, the
bundle, the generated interface, and the controller that implements it.

## Where it lands

Almost nothing is written to the package you name. Every generated file goes in one of three
sub-packages below it, with a single exception:

```
<packageName>.Endpoints  the document's routes as constants — the exception
<packageName>.apis       one interface per group
<packageName>.models     one declaration per schema
<packageName>.utils      the machinery a client needs and a caller mostly does not
```

This is why a document can have a `tags` endpoint group *and* a `Tag` schema: they are `apis.TagsApi`
and `models.Tag`, and neither has to give way. Names still collide **within** a package, and those
are [listed below](#colliding-names).

`Endpoints` sits above the split because it does not belong to either side of it — it is not an API
surface and not plumbing, but the document's own path strings, which a server, a client and a test
all read. See [Endpoints](#endpoints).

## Type mapping

| OpenAPI | Kotlin |
| --- | --- |
| `string` | `String` |
| `string` / `date-time` | `Instant` (which one follows the `ModelStyle`) |
| `string` / `date` | `LocalDate` (kotlinx.datetime, or `java.time` for Jackson) |
| `string` / `uuid` | `Uuid` (`kotlin.uuid`, or `java.util.UUID` for Jackson) |
| `string` / `binary` | `ByteArray` |
| `integer`, `integer` / `int64` | `Int`, `Long` |
| `number` | `Double` |
| `boolean` | `Boolean` |
| `array` | `List<T>` |
| object with properties | a generated model class |
| object with typed `additionalProperties` | `Map<String, T>` |
| object with neither | the free-form type |
| no 2xx response schema | `Unit` |

A `$ref` to a schema with no properties resolves to the underlying type rather than to a class name
— `Upload: {type: string, format: binary}` is a `ByteArray`, not a `class Upload` that nothing would
ever emit.

Optional properties and parameters are nullable and default to `null`, and parameters are ordered so
that everything with a default comes last. Nullability is read from the schema, not inferred from
`required`: a required `nullable: true` property is a non-optional parameter of a nullable type, and
3.1's `type: ["string", "null"]` means the same thing in either order.

## Inline schemas

A schema written in place rather than behind a `$ref` describes exactly as much as a named one, but
the rest of the parser is name-driven — so before anything else reads the document, every inline
schema that would become a declaration is added to `components.schemas` and replaced by a `$ref` to
it (`parser/InlineSchemas.kt`). Nothing downstream knows this happened.

The name comes from the path that reached the schema, so it is predictable from the document alone:

| Where | Name |
| --- | --- |
| `Order.shippingAddress` | `OrderShippingAddress` |
| `Order.lines` items | `OrderLinesItem` |
| `Order.totals` `additionalProperties` | `OrderTotalsValue` |
| `createOrder` JSON request body | `CreateOrderRequest` |
| `createOrder` success response body | `CreateOrderResponse` |
| `createOrder`'s `mode` parameter | `CreateOrderMode` |

`Item` and `Value` are suffixes rather than a guessed singular: `OrderTagsItem` is uglier than
`OrderTag` would be, but singularising `status` gives `Statu`, and a predictable name beats a pretty
one that is sometimes wrong.

Two things are deliberately *not* promoted. A composition branch stays inline — an `allOf` branch is
merged into its container, and a `oneOf` branch must be a `$ref` to be a union member at all, so
promoting either would emit a class no signature mentions. And a schema that becomes no declaration
is left exactly as it was: `{type: object}` with nothing in it is genuinely free-form, and naming it
would generate an empty class rather than describe anything.

The same inline schema written in two places generates **one** class, reused — a document that
repeats one status enum across five operations should not produce five enums a caller cannot pass
between. The name is the first site in document order. Reuse is limited to schemas this pass
created: quietly retyping a property to a component the document never pointed it at would be a
different and much larger claim. A derived name the document already uses is a build failure naming
both, never a silent overwrite.

## Composition

### `allOf`

Flattened into one data class: the branches' properties in document order, then the schema's own,
with `required` the **union** across all of them. Kotlin data classes cannot inherit constructor
properties, so extracting an interface would emit every field twice anyway — the only interface a
generated model set owns is a union base, which is anchored to a real schema name.

Two branches declaring the same property with different types is a document contradiction and fails
the build naming the property and both types. Letting the last branch win is how a generated class
silently acquires the wrong shape, which is exactly the bug this used to have.

### `oneOf` / `anyOf`

A union over object schemas becomes a **sealed interface** its members implement. Members are told
apart either by a declared `discriminator` or, failing that, by the properties they carry.

| | with `discriminator` | without |
| --- | --- | --- |
| kotlinx | `JsonContentPolymorphicSerializer` keyed on the tag | the same, keyed on the properties present |
| Jackson | `@JsonTypeInfo(use = NAME, include = EXISTING_PROPERTY, visible = true)` + `@JsonSubTypes` | `@JsonTypeInfo(use = DEDUCTION)` |
| Unknown variant | a generated `Unknown<Union>` subtype carrying the tag | an error naming the keys seen |

The whole design turns on one thing: **the discriminator is a property the document already
declares**, so neither library may write a second one.

- kotlinx's built-in polymorphism refuses outright — *"cannot be serialized as base class … because
  it has property name that conflicts with JSON class discriminator"* — so the generated serializer
  selects from the content and leaves the property to write itself. The tag is emitted as a constant
  default with `@EncodeDefault(ALWAYS)`, because kotlinx does not encode defaults otherwise and the
  discriminator would go missing from everything the client sends.
- Jackson needs `As.EXISTING_PROPERTY` for the same reason, plus `visible = true` — without it
  Jackson consumes the tag and the Kotlin property has nothing to bind to.

Both were verified by compiling the generated output and round-tripping it: the two styles emit
byte-identical JSON for the same value.

Three limits worth stating rather than discovering:

- **`anyOf` is generated identically to `oneOf`**, which is a real narrowing. `anyOf` means *at
  least* one branch validates, so a payload legal against two of them loses the second.
- **A union over anything but object schemas is not generated.** A sealed hierarchy needs its
  members to implement an interface and `String` cannot, so `oneOf: [{type: string}, {type: integer}]`
  stays raw JSON rather than becoming a type nothing could deserialize into.
- **Members nothing can tell apart fail at parse time**, naming both — not at the consumer's first
  request. `oneOf: [X, {type: "null"}]` is not such a case: that is 3.1's nullable idiom, and it
  resolves to a nullable `X`.

## Enums

A schema with `enum:` becomes an `enum class`, not a `String`. Generated enums are **tolerant**: they
carry an extra entry for values the document does not list, so a server that deploys a new value
does not break clients compiled against the older document.

```kotlin
public enum class Status(
    @get:JsonValue public val wireValue: String,   // @get:JsonValue in the Jackson style only
) {
    ACTIVE("active"),
    IN_PROGRESS("in-progress"),
    UNKNOWN("__unknown__"),
    ;

    override fun toString(): String = wireValue

    public companion object {
        @JvmStatic
        @JsonCreator
        public fun fromWireValue(wireValue: String): Status =
            entries.firstOrNull { it.wireValue == wireValue } ?: UNKNOWN
    }
}
```

Four decisions worth knowing, each of which was measured rather than assumed:

- **The wire value is a property, never an annotation.** That is what frees an entry name from the
  value it stands for — `in-progress` and `2xx` and `""` are all legal in a document and none is a
  Kotlin identifier. It also gives `toString` something correct to return, so an enum works as a
  path, query or header parameter and not only inside a JSON body.
- **Tolerance is inside the generated code, not in the consumer's configuration.** Jackson's
  `@JsonEnumDefaultValue` needs `READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE` on their
  `ObjectMapper`, and kotlinx's `coerceInputValues` needs it on their `Json` — and this generator
  owns neither. So kotlinx gets a generated primitive `KSerializer` and Jackson gets a `@JsonValue`
  getter plus a `@JvmStatic @JsonCreator` factory. Both work on a bare `Json {}` and on a mapper
  carrying only the Kotlin module.
- **`UNKNOWN`'s wire value is a sentinel no server accepts**, rather than `""` or a plausible one.
  A caller that reads an object holding an unrecognised value and writes it back unchanged then
  fails at the server with a clear error, instead of quietly replacing the real value.
- **The unrecognised raw value does not survive.** An enum constant is a singleton with nowhere to
  keep it. A client that must echo unknown values byte-for-byte needs a different shape than an
  enum, and this generator does not offer one.

The fallback's name is `UNKNOWN` unless the document already uses that value, in which case the real
value keeps the name and the fallback escalates to `UNKNOWN_`. Two values that would produce the same
entry name — `in-progress` and `in_progress` — fail the build naming both, like any other collision.
Only string and integer enums are generated; a float or mixed-type `enum:` keeps the underlying
scalar, which is a stated limit rather than a wrong answer.

## Vendor extensions

A document can say how it wants to become Kotlin, through `x-*`. What is read:

| Key | Where | Becomes |
| --- | --- | --- |
| `x-kotlin-name` | schema, property, operation, parameter, tag | the Kotlin name; the wire name is untouched |
| `x-kotlin-type` | schema, property | a type the consumer already owns; nothing is generated |
| `x-kotlin-value-class` | scalar schema | a `@JvmInline value class` over that scalar |
| `x-kotlin-endpoint` | operation | the name of its entry in `Endpoints`; unlike `x-kotlin-name`, the function keeps its own name |
| `x-kotlin-skip` / `x-internal` | schema, operation | left out of the generated client |
| `x-deprecated-reason` | schema, property, operation | the message inside `@Deprecated` |
| `x-enum-varnames` / `x-enumNames` | enum schema | the entry names |
| `x-enum-descriptions` | enum schema | KDoc on each entry |
| `x-nullable` | schema | nullability, beside `nullable` and 3.1's type sets |

`x-kotlin-name` is also the escape hatch from the three failures in **Colliding names** below: two
schemas that derive one class name, two operations that derive one function name. Renaming is a
Kotlin-side change only, so it never alters a single byte on the wire — and a rename still goes
through the same collision check, so renaming one half of a collision onto the other half fails
rather than overwriting it. On a tag it names the interface outright: prefix and suffix are this
generator's derivation, and a document that states the name is not asking for one to be derived. On
an inline schema it replaces the name derived from the path (`OrderShippingAddressGeo`).

`x-kotlin-type` hands a schema to a type the consuming module already has —
`x-kotlin-type: com.example.money.Money` — and nothing is generated for it. The consumer owns that
type *and* its binding: kotlinx needs it `@Serializable` (or a contextual serializer), Jackson needs
it bindable. That is the trade, and it is why the key is in our namespace rather than inferred.

`x-kotlin-value-class` turns a scalar alias into a `@JvmInline value class`, so an `OrderId` cannot
be passed where a `CustomerId` belongs and still costs nothing at runtime. Only a scalar: a value
class holds exactly one value, so a schema with properties or an `enum` is a document saying two
things, and fails. Neither library needs an annotation beyond the style's own — kotlinx binds it
through `@Serializable` on the wrapper, and Jackson's Kotlin module handles value classes itself —
which was settled by round-tripping the exact emitted shape through both, including a nullable
field and byte-for-byte agreement between them. `toString` returns the value underneath, for the
same reason a generated enum's does: a path, query or header argument is converted with `toString`.

`x-enum-varnames` is the other escape hatch: two values that derive one entry name (`in-progress`
and `in_progress`) are a build failure otherwise. `x-enumNames` is NSwag's spelling of the same
list; both are accepted, and a document carrying both with different values fails rather than one
winning. A list whose length does not match `enum` fails naming both counts — a short list would
rename the wrong entries and leave the rest derived, which looks deliberate and is wrong.

`x-kotlin-skip` and `x-internal` mean the same thing, and `x-internal` is what Redocly, Bump and
ReadMe already write to keep an endpoint out of a published reference. **Leaving out a schema that
something still points at fails the parse**, naming the schema and every place that refers to it:
the generated source would otherwise name a class nobody declares, and the error would land in a
file the author never wrote. The same check now catches a `$ref` to a component the document does
not define, which used to become a reference to a name nothing would ever generate.

**`x-kotlin-*` is this generator's namespace, and an unrecognised key in it fails the parse**,
naming the key, where it sits, and the nearest key that does exist. A misspelled `x-kotlin-nmae` is
a setting the author meant; generating as though they had said nothing is exactly the silent
degradation the rest of this generator refuses. Everything outside the namespace —
`x-amazon-apigateway-*`, `x-codegen-*`, `x-stoplight`, `x-faker` — is ignored without comment,
because it is not ours to interpret.

## Failures

Every response that is not 2xx is read. The lowest 2xx becomes the return type, exactly as before;
everything else becomes a `List<ErrorResponse>` on the operation, and a failed call throws.

```kotlin
try {
    client.tags.findTag(id)
} catch (e: ErrorResponseException) {
    e.status          // 404
    e.error.message   // the body, parsed as the schema the document named
}
```

**One exception class per error schema**, named `<Schema>Exception` and generated into the API
package beside a base `ApiException(status, rawBody)`. Per schema, not per status code: the schema
is the document's own vocabulary, while `NotFoundException` would be this generator's invention and
two documents rarely mean the same thing by the same code. A document that uses one `ErrorResponse`
everywhere — as `examples/demo-api/openapi.yaml` does, for all 172 of its declared failures — gets
exactly one.

Thrown, not returned. A sealed result type would let the compiler force the caller to handle the
failure, but it would change every generated signature and would have to be built twice, once per
client style — and the two styles agreeing on their signatures is the property this generator has
kept since its first version.

Four things end at the base `ApiException`, with `rawBody` carrying whatever arrived:

| The document says | What you get |
| --- | --- |
| `404` with a schema | `<Schema>Exception`, `error` parsed |
| `401` with no body | `ApiException(401, null)` |
| nothing about `418` | `ApiException(418, "…")` |
| `404` with a schema, but the body does not parse | `ApiException(404, "…")` |

The last one is deliberate: an error path that throws its own exception while reporting a failure
hides the failure it was reporting.

The document's `default` response becomes the client's `else` — it is what the document says about
a status it did not enumerate. An error body that is not a `$ref` to a component schema (a bare
string, a list, a type named by `x-kotlin-type`) gets no typed exception, because that is the only
shape the generated dispatch can decode without reflection; the failure still reaches the caller as
`ApiException`.

Each style needs one piece of wiring, generated but installed by the consumer, because the consumer
owns the HTTP client — see [`plugins/openapi/README.md`](../plugins/openapi/README.md).

## Authentication

`components.securitySchemes` and `security` are read, including the override rule: an operation
that declares its own `security` **replaces** the document root's rather than adding to it, and
`security: []` replaces it with nothing. Both "declares nothing anywhere" and "`security: []`"
arrive at the client as *no credential*, because to a caller they are the same instruction.

`ApiAuthConfig` is generated with one slot per declared scheme — including schemes no operation
currently requires, since a document that declares one is describing a surface its author expects
to use. Each slot is a suspending function called per request, so a token that expires can be
replaced behind it, and a slot that is null or returns null sends nothing.

| Scheme | Slot | Where the credential goes |
| --- | --- | --- |
| `http` / `bearer` | `(suspend () -> String?)?` | `Authorization: Bearer <token>` |
| `http` / `basic` | `(suspend () -> BasicCredentials?)?` | `Authorization: Basic <base64>`, encoded here |
| `apiKey` in `header` | `(suspend () -> String?)?` | that header |
| `apiKey` in `query` | `(suspend () -> String?)?` | that query parameter |
| `apiKey` in `cookie` | `(suspend () -> String?)?` | that cookie |
| `oauth2`, `openIdConnect` | `(suspend () -> String?)?` | `Authorization: Bearer <token>` |

The slot name is the scheme's own name in camel case: `Bearer` becomes `bearer`, `X-Api-Key`
becomes `xApiKey`.

**`oauth2` and `openIdConnect` are not refused.** This generator cannot run a flow, but what a flow
produces is an access token, and RFC 6749 sends it in the same header a bearer scheme uses — so a
caller that has obtained a token can use one. What the slot's KDoc says is that obtaining it is the
caller's job.

What *is* refused is an `http` scheme this generator does not know — `digest`, `negotiate` — where
the credential is the answer to a server challenge rather than a value the caller holds. Declaring
one parses; an operation **requiring** one is an `EmitException` naming the operation and the
scheme.

## Endpoints

Every operation becomes a constant, so the one thing a spec-first application still typed by hand
stops being typed by hand: the route.

```kotlin
public data class Endpoint(
    public val method: String,        // "PATCH"
    public val `value`: String,       // "/orders/{id}/status"
    public val operationId: String,   // "changeStatus"
    public val summary: String?,      // "Move an order along."
) {
    public val label: String get() = "[$method] $value"
}

public object Endpoints {
    public val PATCH_ORDERS_ID_STATUS: Endpoint = …
    public val all: List<Endpoint> = …
}

public fun Endpoint.path(vararg values: String): String
```

**The name is the verb and the path**, uppercased with every separator becoming `_`: `PATCH` +
`/orders/{id}/status` gives `PATCH_ORDERS_ID_STATUS`. A template variable's braces are punctuation
like any other, which is what makes the name readable — and camel humps inside one are split, so
`{orderId}` reads `ORDER_ID` rather than `ORDERID`. Derived from the path rather than from the
`operationId` because a test name is read far more often than it is written, and `PUT_ORDERS_ID` says
where the request goes.

That derivation is also why two paths can reduce to one name; see [Colliding names](#colliding-names).

**`label` is computed, not stored.** One definition of the `[PATCH] /orders/{id}/status` format, so it
cannot drift between endpoints. It is written for a test name:

```kotlin
feature(Endpoints.PATCH_ORDERS_ID_STATUS.label) { … }
```

**`path` fills the template in order**, and throws on the wrong number of values rather than handing
back a URL with a literal `{id}` still in it — which answers 404, and a 404 names nothing.

**`all` is why this is an `object` and not an `enum`.** The listing an enum gives away for free,
handed back: a spec can assert the document has no untested route by iterating rather than by
remembering.

**A `data class` and not a `value class`.** A Kotlin value class carries exactly one constructor
property; an endpoint is four facts. Packing them into one delimited string and splitting it in the
getters would trade a compile-time record for string surgery on every read.

**Emitted for every `client`, `None` included.** Endpoint constants are not an API surface — that
rule is about an exception nothing can throw, not about the document's own strings — and a
hand-written server is the case with the most to lose, being the only stack with no generated
interface to drift against.

## Colliding names

Two different things in a spec can want the same Kotlin name, and the generator's rule is that a
collision is either merged or fatal — never silently resolved by whichever one is written last.

- **Tags that normalise to one interface name are merged.** `categories-controller` and
  `categories` both mean `CategoriesApi`, so their operations end up in one interface rather than in
  two files racing to the same path.
- **Two operations that would declare the same function name fail**, and the message names both as
  `METHOD /path` so the offending pair is findable in the document.
- **Two schemas that would declare the same class fail**, naming the class. `page_info` and
  `pageInfo` are both `PageInfo`.
- **`writeAllTo` refuses a batch containing two files with the same fully-qualified name**, which
  catches anything the earlier checks did not — writing them in sequence would let the last win.
- **Two operations whose verb and path reduce to one `Endpoints` entry fail.** A category of its
  own, because `Endpoints` is a single object over the whole document: this collides *across* groups
  where every other check here is per group or per file, and two properties of one object are not two
  files, so `writeAllTo` never sees it. Left alone it would surface as *conflicting declarations*
  inside a file nobody wrote. `x-kotlin-endpoint` is the way out — and it is the only one, since no
  `operationId` change can separate two operations that share a verb and a path shape.
- **A generated exception that would take a name the `utils` package already uses fails.** The
  exception for a schema is its name plus `Exception`, so a schema called `Api` derives
  `ApiException`, the base class every other one extends. Rename the schema with `x-kotlin-name`.

An interface and a schema are *not* on that list. They are in different packages now, so `Tag` the
endpoint group and `Tag` the schema coexist.

Where a spec is merely *ambiguous* rather than contradictory, the choice is pinned down instead of
left to document order: an operation with several 2xx responses takes its return type from the
**lowest** one, so `200` wins over `201` however the YAML lists them.

## What it does not handle

- **`not`, and the validation keywords.** `minLength`, `pattern`, `minimum`, `maxItems` and the rest
  are read by nobody: they constrain values, and this generator emits types. A schema using them is
  generated as though they were absent rather than rejected.
- **`anyOf`'s "more than one may match" semantics.** It emits identically to `oneOf`, so a document
  that genuinely means "either shape, possibly both" gets a type that can hold only one.
- **A `oneOf` whose branches are not all `$ref`s to object schemas.** That is not a union this
  generator can name, so the property keeps the free-form type.
- **Float and mixed-type enums.** Only `string` and `integer` enums become enum classes; the rest
  keep the scalar underneath, which is a visible limitation rather than a wrong one.
- **Path-item-level `parameters`.** OpenAPI lets a path item declare parameters shared by all its
  verbs; only an operation's own `parameters` are read. This one is worth more than a line, because
  it is the only entry here that produces a *wrong signature* rather than a loose type — see below.

An unsupported request media type is an error naming the operation, not a silently skipped endpoint
— the same is true of a multipart body with no declared properties, and of every case above where
the text says "failure" rather than "falls back".

### Path-level parameters go missing without a word

Declared on the path item, a parameter is dropped from every operation under it. Given

```yaml
# paths/orders_id.yaml
parameters:
  - $ref: ../components/parameters/id.yaml
get:
  operationId: findOrder
  responses: { … }
```

the generator emits `findOrder(): OrderResponse` — no `id`, no warning, and an interface that
compiles. The document is valid: `redocly lint` passes, because the spelling is correct OpenAPI.

The failure therefore surfaces only where something contradicts the signature. In `spring-orders` a
hand-written `@RestController` implements the generated interface, so it failed the build with
`'findOrder' overrides nothing` — which is the layering earning its keep. An API with no implementor
on that interface, or a Ktorfit client generated for a consumer, would have got a route that quietly
drops its path variable and a 404 or a null at run time instead.

**So `$ref` a shared parameter from each operation**, which costs one line per verb and is what
`components/parameters/` is for. The `openapi-spec-first` skill states the rule; this is why.

## The whole thing

Every section above is what the generator makes of one keyword. This is one endpoint travelling the
whole way — a split document, the redocly bundle, the generated interface, and the controller that
implements it. It is `examples/spring-orders`, so it builds and its specs run against the result.

### The document is split, and bundled before the build reads it

```
examples/spring-orders/openapi/
├── openapi.yaml                 # info, servers, tags, and a $ref per path
├── api-docs.yaml                # what redocly bundles the above into — the build reads only this
├── paths/
│   ├── orders.yaml
│   ├── orders_id.yaml
│   └── orders_id_status.yaml
└── components/
    ├── parameters/id.yaml
    ├── schemas/ChangeStatusRequest.yaml
    └── responses/NotFound.yaml
```

```yaml
# openapi.yaml
openapi: 3.1.0
info:
  title: Spring Orders
  version: 1.0.0
tags:
  - name: orders-controller
    description: Placing, reading and moving orders along
paths:
  /orders/{id}/status:
    $ref: paths/orders_id_status.yaml
```

```yaml
# paths/orders_id_status.yaml
patch:
  tags:
    - orders-controller
  summary: Move an order along.
  operationId: changeStatus
  parameters:
    - $ref: ../components/parameters/id.yaml
  requestBody:
    required: true
    content:
      application/json:
        schema:
          $ref: ../components/schemas/ChangeStatusRequest.yaml
  responses:
    '200':
      description: The order in its new status.
      content:
        application/json:
          schema:
            $ref: ../components/schemas/OrderResponse.yaml
    '404':
      $ref: ../components/responses/NotFound.yaml
```

**`redocly bundle orders@v1` before building.** The generator reads `api-docs.yaml` and nothing else,
so a document edited and not bundled generates the *previous* contract and says nothing about it —
which is the one failure mode of this layout, and the reason the alias exists in `redocly.yaml`
rather than a path being retyped at each call site.

### What the build makes of it

```yaml
# module.yaml
  openapi:
    enabled: true
    specs:
      - spec: openapi/api-docs.yaml
        packageName: com.softistx.example.orders.api
        client: Spring
        models: Kotlinx
        interfacePrefix: I
        interfaceSuffix: Service
```

| Setting | What it decides |
| --- | --- |
| `client: Spring` | `@HttpExchange` interfaces. `Ktor` gives a client instead; `None` gives models only, which is what a hand-written Ktor route wants |
| `models: Kotlinx` | `@Serializable` DTOs. Not `Auto`, which gives Jackson — and `stx.json.enabled` puts kotlinx codecs on WebFlux, so a Jackson model fails to encode at the first response |
| `interfacePrefix` / `Suffix` | `orders-controller` → `IOrdersService`. **One interface per tag**, which is what `groupBy: Tag` means and why the tag names above look like class names |

### The controller implements what was generated

```kotlin
@RestController
class OrderController(
    private val service: OrderService,
) : IOrdersService {
    override suspend fun findOrder(@PathVariable id: String) = service.findOrder(id)

    @ResponseStatus(HttpStatus.CREATED)
    override suspend fun placeOrder(@RequestBody body: PlaceOrderRequest) = service.placeOrder(body)

    override suspend fun changeStatus(
        @PathVariable id: String,
        @RequestBody body: ChangeStatusRequest,
    ) = service.changeStatus(id, body)
}
```

**There is no `@PatchMapping` in that file, and no path string.** The verbs, the paths and the
content types are `@HttpExchange` annotations on the generated interface, and Spring's mapping search
walks the whole type hierarchy to find them — so an endpoint cannot disagree with the document,
because nobody typed it twice.

Two things the interface cannot carry, and they are the only handwritten HTTP left:

- **A status other than 200.** The document says 201 for a placed order and 204 for a cancelled one;
  `@HttpExchange` has nowhere to put that, so it is `@ResponseStatus` on the override.
- **The parameter bindings, repeated.** Spring does not reliably inherit parameter annotations, and
  the failure is a 400 at run time rather than anything at build time — which is why they are written
  out again rather than trusted.

Note the constructor takes `OrderService` and not `IOrdersService`: the service implements the same
generated interface, so by-type injection would be ambiguous between the two beans. That is a
consequence of the layering the skill prescribes — repository → service → controller, with the
service on the same interface — and it is what makes a document change break the service too rather
than being absorbed by a controller that quietly drops a parameter.

### And the document is linted, not just bundled

```yaml
# redocly.yaml
apis:
  orders@v1:
    root: ./examples/spring-orders/openapi/openapi.yaml
    rules:
      no-ambiguous-paths: error     # two paths matching one request is a routing bug
      security-defined: off         # this demo authenticates nobody; declaring a scheme would lie
```

Turning a `recommended` rule off is a decision worth a comment next to it. `security-defined: off`
is there because declaring a scheme the application does not enforce makes the document lie, which
is worse than the warning it silences — and the line goes the moment the example grows an auth
scheme.
