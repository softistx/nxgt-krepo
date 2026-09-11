# stx-spring-boot

The Spring Boot seam — the same job `stx-ktor` does for Ktor, and the same shape: what belongs to
the framework rather than to any one library, one package per concern.

**`io.github.softistx:stx-spring-boot`** — [how to depend on it](../../docs/consuming.md).

```
com.softistx.spring.error    ApiException, ErrorResponse, the advices that connect them
com.softistx.spring.i18n     the request's locale, and the catalogs bound to it
com.softistx.spring.web      what a functional route reads off a request and answers with
com.softistx.spring.client   a typed HTTP client from an interface
com.softistx.spring.security who is calling, and the annotations that say who may
com.softistx.spring.cors     a browser policy read from configuration
com.softistx.spring.json     kotlinx-serialization as WebFlux's codec
com.softistx.spring.data     the Spring Data layer — Mongo's query vocabulary, paging and wiring
```

`examples/spring-orders` is all of it running: a Spring Boot application with **no configuration
class at all**, whose whole wiring is `stx.*` lines in `application.yaml`, and whose spec drives it
over HTTP against a real MongoDB.

An application needs nothing beside `//libs/stx-spring-boot` to use any of this. The one dependency
worth knowing about is `kotlinx-coroutines-reactor`, which is `exported` deliberately: `body<T>()`,
`existsBy<T>()` and `findAsFlow<T>()` are `inline`, so the `awaitSingle`/`asFlow` calls in them are
compiled into the *caller* and have to resolve on the caller's own classpath.

## Everything is opt-in

Every auto-configuration here is `@ConditionalOnProperty(… havingValue = "true")` with **no
`matchIfMissing`**. On the classpath is not the same as switched on:

```yaml
stx:
  errors:
    enabled: true
    include-debug-message: false   # and leave it false outside development
```

A starter that takes over error handling the moment it appears on a classpath is the kind of
surprise that gets a library removed from a project. Nothing here starts, registers or intercepts
anything until an application asks for it by name, and every bean is `@ConditionalOnMissingBean` so
an application's own always wins.

Every key, its default and what it costs is
[`docs/spring-configuration.md`](../../docs/spring-configuration.md) — the other half that grows a
row per capability.

**And where a `stx.*` key overlaps one of Spring Boot's own, Boot's wins.** A `stx.*` key is a better
default than the framework's, never an override of what the application asked for by name — so
`spring.web.locale` beats `stx.i18n.fallback`, and `stx.i18n` then contributes only its catalogs.

That is not free, because both sides express their beans with `@ConditionalOnMissingBean` and the
outcome is therefore a property of *ordering* — plausible either way until it is measured. It was
measured, and it was backwards: Boot's `LocaleContextResolver` won in every arrangement, including
with no `spring.web.*` property set at all, so `stx.i18n.languages` and `stx.i18n.fallback`
configured a bean that never reached a request. Nothing failed and no spec noticed; only the
reference page described what was supposed to happen. `LocaleResolverPrecedenceTest` now pins both
directions, and each half of the fix fails it on its own.

## Configuration metadata is written by hand

`resources/META-INF/additional-spring-configuration-metadata.json` is what an IDE completes `stx.*`
from, and it is maintained by hand rather than generated.

**It cannot be generated here.** `spring-boot-configuration-processor` is a *Java* annotation
processor; the Kotlin Toolchain has no kapt, and its `settings.java.annotationProcessing` runs javac
over Java sources only — so the processor never sees a Kotlin `@ConfigurationProperties` class. This
is not a gap to work around later; it is the shape of the toolchain.

Hand-writing it is not the compromise it sounds like. For a library the metadata is part of the
public surface: it is what someone reads to discover a setting, and reviewing it in the PR that adds
a property is better than regenerating it silently. What that costs is drift, so
`ConfigurationMetadataTest` scans the module for `@ConfigurationProperties` classes and fails when a
constructor parameter has no entry, when an entry names a property that no longer exists, when an
entry omits its type, description, source or default, or when a key escapes the `stx.` namespace.

**Add the entry in the change that adds the property.** The spec will fail otherwise, which is the
point.

## Errors

`ApiException` carries the status a failure should arrive as, and a **translation key** rather than a
sentence:

```kotlin
throw ApiException.notFound("orders.not-found", mapOf("id" to id))
```

`ApiExceptionHandler` resolves that key against the catalogs for the requesting locale and answers
with one `ErrorResponse` shape. The client gets the text in its language; the service that threw
never had to know which language anyone reads.

Three decisions worth knowing:

- **The code is the key, not the message.** `code` is what a client branches on and it defaults to
  the untranslated key. Text changes the day someone improves a sentence; a client matching on text
  breaks that day.
- **There is no `handle(Exception)` catch-all.** Only failures whose text is safe to show get a
  handler. An unexpected exception is left to Spring, which answers with a problem detail that gives
  nothing away — where a catch-all would put a connection string or a constraint name in a response.
- **`debugMessage` is withheld unless `stx.errors.include-debug-message` is on.** What a thrower
  calls a debug message is routinely a query, a constraint name or an upstream body, and a response
  is the one place that reaches someone who was never meant to read it.
- **`timestamp` is a `String`, and that is the interesting one.** It holds ISO-8601 text rather than
  a `kotlin.time.Instant`, because the two codecs an application might have installed do not agree
  about that type: Jackson — which is what WebFlux uses until something replaces it — writes
  `{"epochSeconds":…,"nanosecondsOfSecond":…}`, while kotlinx writes `"2026-08-29T18:21:34.686Z"`.
  Both serialize happily and only a client trying to read a timestamp finds out. A body whose shape
  depends on a codec somebody may or may not have configured is not a contract.
  `ErrorResponseWireTest` pins both codecs to the same string; `ErrorResponse.instant` parses it back
  for a caller who wants the value.

`ValidationExceptionHandler` is a separate class for a mechanical reason: Jakarta Validation is
`compile-only` here, and a `@RestControllerAdvice` naming `ConstraintViolationException` in a method
signature cannot be loaded when that class is absent. Splitting it lets the auto-configuration guard
that one with `@ConditionalOnClass` and register the other regardless.

An enabled `stx.errors` needs a `Messages` bean — declared by the application, or contributed by
`stx.i18n`. There is deliberately no fallback that skips translation: a body reading
`orders.not-found` in production is worse than a context that refuses to start and names the missing
bean.

## Routes

Everything in `web/` is an extension, and none of it is a bean — a functional route reads its
request and returns its response, and there is nothing to inject.

```kotlin
suspend fun list(request: ServerRequest): ServerResponse {
    val category = request.requiredParam("category")
    val page = products.page(category, request.page, request.size, request.sort)
    return page.response().ok()
}

suspend fun create(request: ServerRequest): ServerResponse {
    val body = request.validBody<ProductRequest>(validator, ValidationGroups.Create::class)
    return products.create(body).created()
}
```

- **Nonsense in a paging parameter is not a failure.** `?page=abc` and `?size=-1` are how a
  hand-written link arrives; answering the first page is more useful than a 400 on a parameter the
  caller did not mean to send. A parameter the route genuinely needs is `requiredParam`, which *does*
  fail — as `params.required`, with the parameter's name in the arguments, so the client is told
  which one.
- **`sort` does not return Spring Data's `Sort`.** It returns a list of `SortOrder`, because
  spring-data-commons has no business on the classpath of a consumer that only wanted to read a query
  parameter. Each store's package translates it into what that store sorts by.
- **A sort clause has to match end to end.** The property reaches a query as a field name, so the
  pattern that accepts it is narrow and anchored. `$where:ASC` *contains* a legal clause; a scanning
  parser sorted by `where`, which is an operator and not a field. `SortOrderTest` pins that.
- **`Response<D, M>` has no `error` field, deliberately.** An error never comes back through a
  route's return value here — it is thrown and answered by `ApiExceptionHandler`. A `data`/`error`
  union means every client unwraps two levels to learn something failed, when the status already said
  so. Using the envelope at all is a route's choice; nothing here returns it for you.
- **`Validator.check` throws instead of returning violations**, because a caller who forgets to
  look at a returned set has written a route that validates nothing and says so nowhere. It lives in
  its own file for the `compile-only` reason below.

## Calling another service

```kotlin
interface Catalog {
    @GetExchange("/products/{id}")
    suspend fun product(@PathVariable id: String): Product
}

val catalog = httpClient<Catalog>("https://catalog.internal")
```

**An error response arrives as an `ApiException`, not a `WebClientResponseException`.** A failure
from a service upstream and one raised in this one should reach a handler as the same type, or the
translation gets written twice — and the second time is after the first outage. The upstream's status
is carried across, so its 404 is a 404 here rather than a 500.

The upstream's body is read as a map rather than as `ErrorResponse`: an upstream that is not one of
ours answers in its own shape, and a decoder that throws while handling an error would replace a
useful 502 with a serialization failure. A body with no `message` becomes `errors.unexpected`, which
the caller's own catalogs can still translate.

The `headers` hook runs **per request**, which is what makes it usable for the header that actually
varies — a token read off the current request, a correlation id. `defaultHeaders` would pin the first
caller's value onto every later call.

**An API split across tags takes one factory and a client per interface.**

```kotlin
val factory = httpServiceFactory(baseUrl, headers = { it.setBearerAuth(token) }) { … }
val orders = factory.withClient<IOrdersService>()
val health = factory.withClient<IHealthService>()
```

`httpServiceFactory` is `httpClient`'s other half, and its `factory` parameter is the escape hatch
for the two things that belong to the proxy rather than to the `WebClient` — a conversion service and
a request-values processor. A factory built per interface rebuilds the client underneath it, and the
second interface is where one of those settings quietly goes missing.

**A client for a *generated* interface takes four settings, and `generatedApiFactory` is all four.**

```kotlin
val factory = generatedApiFactory(
    baseUrl,
    json = stxWebJson,                          // only with `models: Kotlinx`
    enums = ::registerApiEnumConverters,
    operations = apiOperationProcessor(),
    filters = listOf(apiAuthFilter(credentials), apiErrorFilter()),
)
val orders = factory.withClient<IOrdersService>()
```

The arguments are the symbols `plugins/openapi` emitted into that module's own
`<packageName>.utils` — same shapes everywhere, different classes — so they are passed rather than
named, and their Spring types are what lets the assembly be written once. **None of the four fails at
build time when left out**, which is the whole reason it is a function and not a paragraph: without
`json` a `kotlin.time.Instant` goes to Jackson, without `enums` Spring writes `IN_PROGRESS` where the
document says `in-progress`, without `operations` a generated filter cannot tell which operation it is
looking at, and without the error filter a documented failure arrives untyped. This is also how a
spec drives a controller through the interface it implements; the `openapi-spec-first` skill has that
convention, and `examples/spring-orders/test/TestHelper.kt` is the whole of what an application writes.

## What is not here

**Reactor await extensions.** `kotlinx-coroutines-reactor` and `kotlinx-coroutines-reactive` already
have them, and a second set of shorter names over the same functions is a vocabulary to learn rather
than a capability to use. The one thing worth knowing is which artifact a given name is in:
`awaitSingleOrNull` on a `Mono` is the Reactor one, `awaitFirstOrNull` on a `Publisher` is the other,
and this module depends on both.

## Who is calling

```kotlin
val owner = requireCurrentUser().username
```

**`ReactiveSecurityContextHolder`, never `SecurityContextHolder`.** The non-reactive holder is a
`ThreadLocal`, and in WebFlux a request is not a thread — the same trap the locale section below
describes, in the other half of the stack. `currentUser()` returns null for an anonymous request,
because an endpoint that anyone may read and that is *richer* when signed in is an ordinary thing to
write; `requireCurrentUser()` is for the rest and fails as `errors.unauthorized`.

Four annotations say who may call what:

```kotlin
@RequireRole("ADMIN")
suspend fun archive(id: String)

@RequireOwnership
suspend fun order(id: String): Order
```

- **`@RequireRole` and `@RequireAuthority` are `@PreAuthorize`.** The versions these came from were
  `@PostAuthorize`, which runs the method *first* and denies afterwards — so a caller without the
  role still got their write performed and only the response refused. A check on who may call
  something has to happen before the something.
- **`@RequireOwnership` and `@FilterByOwnership` are genuinely post-hoc**, because whether the caller
  owns a thing cannot be known until the thing is loaded. Put `@RequireOwnership` on a read; on a
  delete, the delete happens and only the answer is refused.
- `@FilterByOwnership` filters what a query already returned, so the database read every row and a
  page can come back short. A query that says `createdBy = me` is the better answer wherever one can
  be written.
- The expressions read `returnObject.metadata.createdBy`, which is the shape `stx-mongo`'s `Audited`
  gives a document. Nothing here depends on that module — SpEL resolves the path at runtime.

**Two things have to be switched on for these to work.** `stx.security.enabled` registers the
`AnnotationTemplateExpressionDefaults` bean, without which `{value}` is never substituted and
`@RequireRole("ADMIN")` denies every call as the literal expression `hasRole('{value}')`. And
`@EnableReactiveMethodSecurity` is the application's to add, because turning method security on
changes how every bean in the context is proxied — not something a dependency should do quietly.

`stx.security` contributes a `PasswordEncoder` and that one bean, and nothing else. The filter chain,
the permitted paths and the authentication manager are application policy: a library that guessed at
them would either lock a service out of its own health check or open something that should not be.

## Browsers

```yaml
stx:
  cors:
    enabled: true
    origins: [ "http://localhost:5173" ]
```

**The properties become a `com.softistx.common.http.CorsPolicy`, and Spring's `CorsConfiguration`
after that.** `stx-ktor` installs Ktor's plugin from the same policy, so an application moving
between the two frameworks keeps its origins, its methods and its keys — CORS is a browser policy,
not a web-framework feature, and two configuration classes that agree today agree only for as long
as somebody keeps them agreeing.

`origins` is empty by default, because a browser policy that arrives already permitting somebody is
the wrong shape of default. Everything else defaults permissively — once an origin is trusted,
restricting which methods it may use adds nothing an attacker at that origin cannot work around.

**One combination fails at startup on purpose.** `origins: ["*"]` with `allow-credentials: true` is
forbidden by the CORS specification, and Spring throws when the *request* arrives rather than when
the bean is built — which turns a configuration mistake into an intermittent browser failure found by
whoever is testing the front end. The policy refuses it while the context is starting, and names
`stx.cors.origin-patterns` — which is what actually does the job, since the concrete requesting
origin is echoed back rather than a wildcard.

## JSON

```yaml
stx:
  json:
    enabled: true
```

Off by default, and that is not timidity. Jackson is what WebFlux uses until something replaces it,
and it serializes anything; kotlinx serializes what carries `@Serializable` and throws on the rest.
Switching a running application over is a decision with a blast radius.

The `Json` is built **from** `stx-common`'s `lenientJson` rather than beside it, so unknown keys stay
ignored for the reason that module gives — a reader that throws on a field a newer writer added stops
during every rolling deploy. Two settings are added on top, and both are about a *response*
specifically: nulls are not written (a type with a dozen optional fields is otherwise mostly nulls),
and defaults are (a client that has never seen a field cannot know what the server would have used).

The two interact, which is worth knowing before someone turns on `explicit-nulls` and finds their
nulls still missing: a property equal to its default is dropped first, and for a `String? = null` the
default *is* null.

It is contributed as a `CodecCustomizer`, not by implementing `WebFluxConfigurer` — a configurer is a
whole extension point with a dozen methods, and an application that has its own would find two of
them competing.

## Mongo queries — see `docs/spring-mongo-queries.md`

`data/mongo/` is the Spring Data Reactive Mongo half: a predicate DSL, a filter grammar for query
strings, and the converters without which a `kotlin.time.Instant` cannot be a field.

```kotlin
val cheap = all(Product::stock gt 0, Product::price lte 50).query
val listed = template.find(request.mongoQuery, Order::class.java)   // ?filter= and ?sort= together

val page = template.findPage<Order>(request.mongoPage())            // …plus ?size= and ?cursor=
```

The vocabulary — every operator in both forms, the filter tokens and how each reads its value, the
sort grammar, and the three rules a keyset cursor has to obey — is
[`docs/spring-mongo-queries.md`](../../docs/spring-mongo-queries.md). It gains an entry every phase,
which is the signal it does not belong here. What stays below is why the package is shaped that way
at all.

**A field named by a string is a name nothing checks.** Rename the property and the query still
compiles and silently matches nothing — which reads as "no results", not as "broken query". So every
operator has a `KProperty` form, and that is the one to reach for; the string forms remain for fields
that have no property to name them.

**A filter fails loudly where a sort shrugs**, and the asymmetry is the most important decision in
the package. An unreadable `?filter=` clause is a 400; an unreadable `?sort=` clause is dropped.
Dropping a filter returns *more* rows than the caller asked for, so `status:eq:PIAD` would answer
with the whole collection rather than an empty page. Sorting can afford to shrug; narrowing cannot.

**A page's ordering has exactly one source.** `mongoQuery` bakes `?sort=` into the query, which is
what `find` wants and what a keyset page must not be given: the cursor is built from the window's
`sort`, so an ordering arriving any other way produces a right-looking first page and an empty
second one. `request.mongoPage()` is the correct spelling, `MongoPage` refuses the other, and
`PageSortSourceTest` pins the row the defect used to lose — this README demonstrated it for four
slices.

**Paging is keyset, not `skip`.** An offset page re-reads every row it skips, so page 500 costs five
hundred pages of work — and a row inserted while a client is paging shifts every later page by one,
so the client sees a row twice or never. A cursor resumes from a key: constant cost, stable under
writes. The window is `stx-common`'s `PageWindow`, the same one `stx-mongo` and `stx-jpa` implement.

### Wiring

The keys are in [`docs/spring-configuration.md`](../../docs/spring-configuration.md); the ordering is
the part worth explaining here.

**The configuration is registered before Spring Boot's, and that is the whole trick.** Boot's
`MongoCustomConversions` bean is `@ConditionalOnMissingBean`, so a library contributing one *after*
it never applies, and one contributing it without ordering replaces Boot's — quietly dropping
`spring.data.mongodb.representation`. Registering first and carrying that property across is the only
arrangement where both the `Instant` converters and Boot's own setting survive. A spec asserts both
in the same context.

The rest is opt-in individually: a GridFS template only when a bucket is named, a transaction manager
only when asked for (and note that transactions need a replica set — a standalone `mongod` fails the
first `startTransaction`, not startup), and a `ReactiveAuditorAware` reading the *reactive* security
context, so `@CreatedBy` stamps the request's user and not whoever last used the worker thread.

`@EnableReactiveMongoAuditing` stays the application's to add. It changes how every entity is
persisted, which is not something a dependency should do quietly.

**Index creation never drops.** The version this replaces dropped every index on every collection
and rebuilt them at each startup, which is an outage waiting for a large collection: while an index
rebuilds, every query that used it scans — once per instance on a rolling deploy. Creating an index
that already exists is a no-op in Mongo, so create-only is idempotent and safe on every boot, and an
index no longer declared is left alone because deciding it is unused is a migration's job.

Idempotent per feature is not the same as idempotent together, which is the one sharp edge here.
**Anything else that creates an index has to agree with this one about its name.** Mongo refuses a
second `createIndex` over the same keys under a different name, and the two spellings do not match by
accident: Spring Data names an `@Indexed` index after the property, while an unnamed `Index()` gets
Mongo's own `code_1`. Two switches that are each safe on every boot then abort each other the first
time both are on — which is how `examples/spring-orders` found it. A `createIndex` written by hand
anywhere near a mapped type wants an explicit `named(…)`.

That is also why `stx-migrations`' Mongo ledger has **no secondary index at all**: the version is the
`_id`, so uniqueness is the primary key and there is nothing left for this to collide with.

## The audit trail

```yaml
stx:
  data:
    mongo:
      audit:
        enabled: true
        collection: audits
```

```kotlin
@Auditable
@Document("orders")
data class Order(@Id val id: String, val status: String, val total: Int)
```

That is the whole setup. Every save and delete of an `@Auditable` document appends an `AuditEntry`
carrying the document's full state, the properties that changed, who changed them, and a version
number that only goes up. Nothing is ever updated — a history that can be edited is not one.

The diff comes from [Javers](https://javers.org), which is `compile-only`: an application that does
not audit anything does not carry it.

**Opt-in per document, not per application.** An audit trail on everything is a second copy of the
database that nobody budgeted for. This is for the collections where *who changed this, and to what*
is a question somebody will actually ask.

**Saves that changed nothing record nothing.** Spring Data emits an `AfterSaveEvent` for every save,
including the ones that wrote the same values back, and a trail full of versions that differ in
nothing is a trail nobody reads.

**A delete is `TERMINAL` and the last word.** It keeps the last known state — the question asked of a
deletion is nearly always *what was it when it went* — and saves of the same id afterwards are
ignored rather than continuing the history. A new document reusing an id is a different thing, and
stitching the two together would produce a diff between two unrelated objects, presented as a change
somebody made.

**The write is asynchronous, on a scope the context owns.** Auditing runs after the save has already
happened, so failing it cannot undo anything, and blocking the request on a second write would cost
every caller latency for a record nobody is waiting on. The consequence stated plainly: an entry can
be lost if the process dies between the save and the append. The scope is the `stxAuditScope` bean —
replaceable, and cancelled when the context closes. It is deliberately **not** `GlobalScope`, which
would keep writing through shutdown, be stopped only by the process exiting, and give a test nothing
to wait for.

**The collection name is not a SpEL `@Document`.** A configurable mapped collection is normally
written `@Document("#{@environment.getProperty(…)}")`, because the annotation is read at mapping time
and no bean of ours runs early enough to rename it. That expression needs a bean resolver, so it
resolves only inside an application context: a `ReactiveMongoTemplate` built by hand fails with
`EL1057E: No bean resolver registered`, which is how this was found. `AuditStore` holds the name and
passes it to the template's `collectionName` overloads instead.

`stx.data.mongo.audit` is not `stx.data.mongo.auditor`. The latter registers Spring Data's
`ReactiveAuditorAware` so `@CreatedBy` stamps *who* onto the document itself; this one keeps the
document's whole history in a collection of its own.

## Migrations — see `libs/stx-migrations`

**They are not here any more, and the move was the point.** This module used to carry a MongoDB
migration runner: a version parsed out of the class name, no lock, `PENDING`/`APPLIED`/`FAILED`, and
a suspending `@EventListener(ApplicationReadyEvent)` — which, per `SuspendingListenerTest`, Spring
does not wait for. The port opened while migrations were still being applied, and a process killed
mid-migration was indistinguishable from one that never started, so the next boot re-applied it
without a word.

`stx-migrations` is the replacement, written for both stores and for both stacks:
`stx.migrations.enabled` with `stx-migrations-spring` on the classpath, a version the class
*declares*, a renewing lease, a `RUNNING` status that makes a killed process fail closed, and a gate
that is an `InitializingBean` — a failure means no web server, not a log line.
[`docs/migrations.md`](../../docs/migrations.md) is the vocabulary.

What stays here is one bean, because it is Spring Data's half of the bridge and not the migration
library's:

```kotlin
@Bean
@ConditionalOnMissingBean
fun stxCoroutineMongoDatabase(factory: ReactiveMongoDatabaseFactory): MongoDatabase =
    MongoDatabase(runBlocking { factory.mongoDatabase.awaitSingle() })
```

`stx.migrations.store: mongo` asks the context for a coroutine `MongoDatabase`, and an application on
Spring Data has a `ReactiveMongoDatabaseFactory` instead. **Turning on `stx.mongo` to get one is the
wrong answer, not a longer one**: that opens a second pool against the same server, and in a suite it
opens one that never saw the per-run database suffix `MongoTestConfiguration` splices into
`MongoProperties` — so the migrations would run against a database nobody chose and every spec would
still pass. Building it from the factory is what makes that suffix arrive.

It wraps the pool Spring Data opened and owns none of it: the coroutine `MongoDatabase` has no
`close()`, so Spring infers no destroy method and the client stays Spring Data's to close. An
application that turns on `stx.mongo` as well keeps *that* database — the ordering is explicit, and
an explicit URI beats an inferred handle.

## The request's locale

```kotlin
messages.forRequest(exchange)["orders.title"]
```

**The locale comes from the `ServerWebExchange`, not from `LocaleContextHolder`.** That holder is a
`ThreadLocal`, and WebFlux is the one Spring stack where a request is not a thread: a handler can
resume on a different worker after any suspension point, and whatever the holder then returns
belongs to whichever request last ran on it. It looks correct in development, where one request is
in flight at a time, and starts serving French to English readers under load — a bug with no stack
trace and no failing test. The exchange follows the request wherever it resumes.

## The stx libraries — one module each

Turning a library on is one `stx.*` block, and the auto-configuration that reads it lives **beside
that library** rather than here:

| property | module |
| --- | --- |
| `stx.mongo` | `libs/stx-mongo/stx-mongo-spring` |
| `stx.jpa` | `libs/stx-jpa/stx-jpa-spring` |
| `stx.redis` | `libs/stx-redis/stx-redis-spring` |
| `stx.kafka` | `libs/stx-kafka/stx-kafka-spring` |
| `stx.amqp` | `libs/stx-amqp/stx-amqp-spring` |
| `stx.storage` | `libs/stx-storage/stx-storage-spring` |
| `stx.i18n` | `libs/stx-i18n/stx-i18n-spring` |

They used to be `com.softistx.spring.integration`, one package each in this module. `stx-workflow`,
`stx-migrations`, `stx-graphix` and `stx-telemetry` already kept theirs beside their own libraries,
so this module was stating one relationship in a second shape — and it cost each of them an honest
dependency: an integration in a hub has to declare its library `compile-only`, because six siblings
are optional, even though its own is not. Beside its library each edge is `exported`.

Each of those modules has a README for the decision worth knowing — why `stx.kafka` opens nothing,
why no `stx.storage` credential has a default, why `stx.jpa` refuses without `packages`, why
`stx.i18n` needs WebFlux when its siblings need only `spring-boot-starter`. `docs/spring-configuration.md`
is still one page for every key.

**What did not move.** `stx.data.mongo` is Spring Data, not a `stx-*` integration, and stays here —
including the bean that bridges Spring Data's own pool to a coroutine `MongoDatabase` so that an
application already on Spring Data can hand that handle to `stx-migrations` without opening a second
client. That bean and `stx-mongo-spring`'s can both contribute a `MongoDatabase`, which is why
`MongoAutoConfiguration` orders itself after that module by name — see `MongoDatabaseOrderingTest`.

`stx-i18n` also stays an `exported` dependency of this module, and that is not a leftover:
`RequestTranslator` and the translated error body are this module's own features, and `Messages` is a
constructor parameter of its exception handler. What moved is the auto-configuration, not the use.


## Testing an application built on this

`com.softistx.spring.testing` is in `src/`, not in a test tree, because it is *for consumers* — an
application adds `//libs/stx-spring-boot` and `//libs/stx-testing` to its `test-dependencies` and
writes four things:

```kotlin
// test/ProjectConfig.kt — in package io.kotest.provided, where Kotest looks for it
object ProjectConfig : SpringProjectConfig()

// test/OrderControllerTest.kt — no bootstrap, no base spec of its own
class OrderControllerTest(template: ReactiveMongoTemplate, json: Json) : MongoSpec({
    beforeEach { template.clear("orders", "audits") }

    feature("POST /orders").config(enabled = mongoAvailable) { … }
})
```

```yaml
# testResources/application-test.yaml
server:
  port: 8088                       # TestServer.STX_TEST_PORT, so a client needs no URL spelled out
spring:
  mongodb:
    database: spring_orders_test   # a prefix: `testDatabase` appends this run's suffix
```

That is the whole surface. `MongoSpec` is `@ActiveProfiles("test")` plus
`@SpringBootTest(DEFINED_PORT)` plus the two `@TestConfiguration`s below; re-annotating a subclass
overrides any of them. `SpringSpec` is the same without Mongo, for an application on something else.

**Where MongoDB is, answered as a bean.** `MongoTestConfiguration` contributes a
`MongoConnectionDetails`, and `MongoReactiveAutoConfiguration` declares its own under
`@ConditionalOnMissingBean` of that type — so the test bean wins outright, with no property registry
and no `@DynamicPropertySource` companion for every application to copy. The URI comes from
`stx-testing`: `MONGO_TEST_URI` when a server is already up, a container started once for the run
otherwise, and neither means `mongoAvailable` is `false` and every feature gated on it reports
skipped rather than red.

**A bean rather than a property, and the reason is a scar.** Spring Boot 4 split the old
`spring.data.mongodb`: the driver's URI, credentials and database are `MongoProperties` under
**`spring.mongodb`**, while `spring.data.mongodb` kept only GridFS and the big-decimal
representation. A `spring.data.mongodb.uri` carried over from Boot 3 therefore binds to nothing, is
reported by nothing, and leaves the driver on `MongoProperties.DEFAULT_URI` — `mongodb://localhost/test`,
which on a developer machine is a real server that answers. `examples/spring-orders` was doing
exactly that, container and all, and its suite passed the whole time. A bean is asked for by type and
cannot be misspelled; `MongoSpecTest` pins the prefix so the next rename fails loudly.

**The database is the run's, and it is given back.** What `application-test.yaml` names is a prefix:
`testDatabase` appends `stx-testing`'s run suffix, and a `BeanPostProcessor` rewrites
`MongoProperties.database` with the result before anything reads it. Both halves are load-bearing.
The suffix is the convention every harness here follows — a fixed name on a server
`MONGO_TEST_URI` points at is shared with whatever else is running, and these specs empty
collections, so two suites at once would clear each other's; a suite run twice would also find its
migrations already in the ledger and never re-seed what the first run cleared. And the *post-processor*
rather than a name spliced into the URI alone, because
`DataMongoReactiveAutoConfiguration.reactiveMongoDatabaseFactory` reads `MongoProperties.getDatabase()`
first and only falls back to the connection string — so the URI-only version left the driver on one
database and `ReactiveMongoTemplate` on another, which is how `MongoSpecTest` found it. Then
`MongoTestCleanup` drops that database at JVM exit, as a shutdown hook and not a bean's destroy
method: the database belongs to the run, and Spring's context cache may close one context while
another is still writing. Against a container it costs a connection and nothing else; against the
workspace's own replica set it is the difference between leaving one database per run behind and
leaving none.

**The port is not a constant anybody keeps in sync.** `TestServer.port` starts at
`STX_TEST_PORT` — what `DEFINED_PORT` binds and what a spec body reads before any context exists —
and `TestServerConfiguration` overwrites it with the port the server actually took, so a subclass
re-annotated `RANDOM_PORT` works too. `webTestClient()` and an application's own client factory
default to it. `bindToServer` and not `bindToApplicationContext`: the kotlinx codecs, the exception
advice, the locale negotiation and the `Instant` converters are auto-configurations, and a client
bound to a context bypasses several of them.

**`extensions` is `final` on `SpringProjectConfig`.** The failure that prevents is an application
adding a listener of its own, overriding `extensions` to say so, dropping `SpringExtension` on the
way, and watching every spec fail on a missing bean. Extras go to the constructor and keep it.

Everything behind this — `stx-testing`, `spring-boot-starter-test`, Kotest — is `compile-only`, for
the reason the next section gives. Nothing in `src/` refers to `com.softistx.spring.testing` and no
auto-configuration imports it, so an application that never writes a spec never loads any of it.

## Dependencies

One rule: **an optional dependency is `compile-only`**. An application that wants the error handling
must not inherit Jakarta Validation and a Mongo driver along with it. What that means in practice is
that a package guarded by `@ConditionalOnClass` is dark until the application adds that library
itself — and that the specs here list those libraries under `test-dependencies`, or they would
assert against conditions that never match.

That rule used to cover seven `stx-*` libraries too. It no longer does, because those integrations
are modules beside their own libraries now, where the same edge is `exported` — a dependency is
optional here and not optional there, and each manifest can finally say which.

That is not theoretical. `ErrorAutoConfigurationTest` found no `ValidationExceptionHandler` on its
first run, because `compile-only` had done exactly what it says — which is what a consumer without
validation sees.

Verify it with `./kotlin show dependencies -m stx-spring-boot`: a compile-only entry is in the
COMPILE scope and absent from RUNTIME.

## Where to read next

| | |
| --- | --- |
| [`../../docs/spring-mongo-queries.md`](../../docs/spring-mongo-queries.md) | What a query may say — the operators, the filter and sort grammars, and the paging rules |
| [`../../docs/spring-configuration.md`](../../docs/spring-configuration.md) | Every `stx.*` key, its default, and what switching it on costs |
| [`../stx-ktor/README.md`](../stx-ktor/README.md) | The Ktor seam — the same split, drawn the same way, and the resource idiom its plugins are built on |
| [`../stx-i18n/stx-i18n/README.md`](../stx-i18n/stx-i18n/README.md) | What `Messages` loads, how a key falls back, and what `Accept-Language` negotiation matches |
| [`../../AGENTS.md`](../../AGENTS.md) | The repo's conventions, including publishing and the catalog |

---

Apache-2.0 · [Contributing](../../CONTRIBUTING.md) · [All the libraries](../../README.md)
