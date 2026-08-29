# stx-spring

Spring Boot integration for the libraries here — the same job `stx-ktor` does for Ktor, and the same
shape: one package per concern, one module for all of them.

```
com.strange.spring.error    ApiException, ErrorResponse, the advices that connect them
com.strange.spring.i18n     the request's locale, and the catalogs bound to it
com.strange.spring.web      what a functional route reads off a request and answers with
com.strange.spring.client   a typed HTTP client from an interface
com.strange.spring.security who is calling, and the annotations that say who may
com.strange.spring.cors     a browser policy read from configuration
com.strange.spring.json     kotlinx-serialization as WebFlux's codec
com.strange.spring.data     the Spring Data layer — Mongo's query vocabulary, paging and wiring
```

More packages arrive in their own changes: the Mongo template and auditing, and one
auto-configuration per `stx-*` library. What follows is true of all of them.

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

**The properties become a `com.strange.common.http.CorsPolicy`, and Spring's `CorsConfiguration`
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

## Mongo queries

`data/mongo/` is the Spring Data Reactive Mongo half: a predicate DSL, a filter grammar for query
strings, and the converters without which a `kotlin.time.Instant` cannot be a field.

```kotlin
val cheap = all(Product::stock gt 0, Product::price lte 50).query
val listed = request.mongoQuery      // ?filter=...&sort=... together
```

- **Every operator has a `KProperty` form, and that is the point.** A field named by a string is a
  name nothing checks: rename the property and the query still compiles and silently matches
  nothing — which reads as "no results", not as "broken query". The string forms remain for fields
  that have no property to name them.
- **`all`/`any`/`none` build an explicit `$and`/`$or`/`$nor`.** A chain of `.and("field")` builds one
  document key per field and quietly loses the second predicate on a field named twice:
  `where("price").gt(5).and("price").lt(10)` is `{price: {$lt: 10}}`.
- **A substring search quotes what it was given.** Otherwise a search box is a way to hand the
  database a regular expression, and `(a+)+$` against a long field is a request that does not come
  back. It does not take a hostile user — only someone searching for `C++`.
- **The negated regex operators are real.** `notContaining` is `where(f).not().regex(...)`, because
  Spring's `Criteria.not()` sets a flag the *next* operator consumes: `(f containing x).not()`
  negates nothing, and the version this was ported from had `!like` behaving exactly like `like`.

### The filter grammar

```
?filter=status:eq:PAID;total:gte:100
?filter=or@email:eq:a@b.c;email:eq:d@e.f
```

Clauses are `field:operator:value`, separated by `;`, combined with `and` unless the parameter starts
with `or@`. The operators are `eq ne lt lte gt gte before after from to like !like ilike !ilike in
!in exists size near within` — a closed set, because a grammar that passed operators through would
let a caller write `$where`, which is JavaScript the server runs.

**A clause that does not parse is a 400, not a clause that is skipped.** This is the one place where
lenience is the wrong instinct, and it is the opposite of what `?sort=` does: dropping a filter
returns *more* rows than the caller asked for, so `status:eq:PIAD` would answer with the whole
collection rather than an empty page. Sorting can afford to shrug; narrowing cannot. For the same
reason a comparison against unparseable text is a failure rather than a zero — the version this came
from coerced it, so `price:gte:cheap` quietly became `price >= 0`.

### Paging

```kotlin
val page = template.findPage<Order>(MongoPage.first(20, query = request.mongoQuery))
```

**Keyset, not `skip`.** An offset page re-reads every row it skips, so page 500 costs five hundred
pages of work — and a row inserted while a client is paging shifts every later page by one, so the
client sees a row twice or never. A cursor resumes from a key: constant cost, stable under writes.

The window — `first`/`last`/`cursor` and the rules about them — is `stx-common`'s `PageWindow`,
the same one `stx-mongo` and `stx-jpa` implement, and the trimming is its `pageOf`. What is specific
here is the Spring Data `Query` and `Sort`, and that every failure is an `ApiException`: a
contradictory window, a page size of zero and a cursor from a different query are all a client
sending something it should not have, so they are 400s and they arrive translated.

Three things the specs pin, each of which is silent when wrong:

- **The ordering always ends in `_id`.** A keyset resumes from the last row's key, so the key has to
  be unique — order by `name` alone and every document sharing a name is a coin toss between being
  served twice and being skipped.
- **Cursors carry the stored field names.** A property with `@Field("t")` is `t` in the document, so
  a cursor built from the property name reads nothing back and every page after the first comes up
  empty. That is also why documents are fetched raw and decoded here rather than mapped by the
  template: a mapped object no longer has the stored values the cursor needs.
- **A cursor from a differently sorted query is refused.** It would page along the wrong key and
  answer with rows that look perfectly plausible.

### Wiring

```yaml
stx:
  data:
    mongo:
      enabled: true
      gridfs-bucket: uploads
```

`stx.data.mongo` and not `stx.mongo`: this is the Spring Data layer, and `stx.mongo` belongs to the
`stx-mongo` library's own integration — two layers over the same driver, and an application may
reasonably use either.

**The configuration is ordered before Spring Boot's, and that is the whole trick.** Boot's
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

### `kotlin.time.Instant`

BSON has one date type and the driver has a codec for `java.util.Date` and none for
`kotlin.time.Instant` — so an entity with one fails at *query* time with `Can't find a codec`, not
at mapping time. `stxMongoConversions()` is the fix. Note that BSON dates hold milliseconds: a round
trip loses anything finer, which matters for a cursor built from a timestamp and not at all for a
`createdAt` somebody displays. Storing a string would keep the nanoseconds and lose range queries and
index ordering, which is the worse trade.

`stx-mongo` has `mongoCodecRegistry()` for the identical gap — two layers over the same driver, each
needing to be told about the same type.

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

## Migrations

```yaml
stx:
  data:
    mongo:
      migration:
        enabled: true
        prefix: V
        collection: migrations
```

```kotlin
@MigrationUnit("backfills every order's currency")
class V3Currencies(private val template: ReactiveMongoTemplate) : Migration {
    override suspend fun migrate() {
        template.updateMulti(Query(), Update().set("currency", "EUR"), "orders").awaitSingle()
    }
}
```

**The class name is the version.** `V3Currencies` is order 3, code `V3`. The name has to match
`<prefix><digits><name>`, and `<name>` has to start with a letter or an underscore — otherwise `V102`
could be read as order 102 or as order 10 followed by `2`, and a regex would pick one silently.

**A `Migration` bean whose name does not match is a loud warning at startup, not a shrug.** A
migration that quietly does not run is the failure this whole mechanism exists to prevent.

**Two units at the same order abort the whole run.** Their codes would collide, one would be
recorded as the other and never run, and which one is arbitrary. An arbitrary migration order is
worse than no migrations.

**A failure stops everything after it, on this startup and every later one.** Migrations are written
against the state the previous one left, so continuing past a failure applies a change to a database
that is not in the shape it expects. The record stays `FAILED` until somebody deals with it.

**Migrations should be idempotent.** The unique index on `code` stops two instances from both
*recording* a migration, but nothing holds a lock while one *runs*, so two instances starting
together can both execute the same `PENDING` unit. Making that impossible needs a lease with a
timeout, and a lease that expires while a long migration is still running is a worse failure than
the one it prevents.

**The runner is a suspending `@EventListener` on `ApplicationReadyEvent`, and Spring does not wait
for it.** `publishEvent` returns while the listener is still suspended, so the application is
serving requests while migrations are being applied — a migration is not a startup gate, and an
exception out of one goes to a reactive error handler nobody reads rather than to whoever published
the event. That is why the runner catches its own failures. `SuspendingListenerTest` pins both
halves; the first version of it asserted the result straight after `publishEvent`, passed on a
`delay(1)` that happened to finish first, and failed on the next run.

Three things here differ from the version this was extracted from, and each was a defect: `enabled`
defaulted to `true`, so putting the library on a classpath was enough to write to the database;
discovery filtered on the `@MigrationUnit` annotation, so a unit declared through an `@Bean` method
was silently ignored; and `interface IMigration` declared a `rollback()` that nothing anywhere
called, which reads as a promise that a failed migration is undone.

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

## The stx libraries

`integration/` is one auto-configuration per `stx-*` library, so a Spring application uses them
without wiring anything.

```yaml
stx:
  mongo:   { enabled: true, uri: mongodb://localhost:27017, database: orders }
  jpa:     { enabled: true, uri: "postgresql://localhost:5432/orders", packages: [ com.acme.domain ] }
  storage: { enabled: true, endpoint: http://localhost:9000, access-key: ${MINIO_KEY}, secret-key: ${MINIO_SECRET} }
  redis:   { enabled: true, uri: redis://localhost:6379, namespace: orders }
  kafka:   { enabled: true, bootstrap: "localhost:9092", client-id: orders }
  amqp:    { enabled: true, uri: "amqp://user:secret@rabbit:5672/billing" }
  i18n:    { enabled: true, languages: [ en, fr ], fallback: en }
```

```kotlin
class OrderRepository(private val orders: MongoDatabase)   // built by the container, nothing to install
```

Every one of them is the same shape, and the shape is the point:

- **`@ConditionalOnClass`**, so the `compile-only` dependency stays optional at runtime. A package
  nobody added the library for is dark.
- **`@ConditionalOnProperty` with no `matchIfMissing`.** Putting `stx-spring` on a classpath opens no
  connection to anything.
- **`@ConditionalOnMissingBean` on every bean**, which is how a deployment sets the things this
  module has no opinion about. TLS, pool sizes and read concerns are not properties here; declaring
  your own `MongoClient` bean is the answer, and the `MongoDatabase` is still built over it rather
  than opening a second pool.
- **Built through that library's own factory** — `mongoClient`, `Jpa.scan`, `ObjectStorage.connect` —
  never by assembling a client here. The factory knows something the caller does not: a Mongo client
  built without `stx-mongo`'s codec registry compiles, connects, reads, and then stores an `Instant`
  as something nothing in that library can read back, with every step succeeding until the data is
  already written.
- **Closed with the context**, through the inferred `close()`. All of these close idempotently via
  `CloseGuard`, so an application that also closes its own is not a problem.
- **A missing required key is a sentence naming the key.** `stx.mongo.enabled is true but
  stx.mongo.uri is not set`, not a binder error naming a constructor parameter.

`stx.mongo` is not `stx.data.mongo`, and neither is Spring Boot's `spring.data.mongodb`. The last two
configure Spring Data's `ReactiveMongoTemplate`; the first hands you `stx-mongo`'s coroutine client.
They are different APIs onto the same server, and turning both on means two connection pools.

**`stx.jpa` and `stx.amqp` block the thread that is starting the application, deliberately.**
`Jpa.connect` and `Amqp.connect` both suspend — reading the annotations off every entity and
standing up a service registry is ordinary blocking work — and a `@Bean` method cannot. That thread
is doing nothing else and is not an event loop, so this is the one place where blocking is the right
answer rather than a shortcut.

**`stx.amqp` opens a socket there and `stx.jpa` does not**, and that difference is also deliberate.
On the default `schema-mode` Hibernate's pool opens its first connection when something asks for a
session, so a wrong password surfaces on first use; any other mode has schema work to do and
connects at startup, which is the point of choosing one. AMQP connects either way — a service whose
work arrives over that connection should fail its boot when the broker is not there, rather than
start and quietly consume nothing.

**`stx.kafka` opens nothing at all, and has no `close()` to call.** `Kafka` is deliberately not a
`connect()`: a Kafka client connects when it is constructed, so the connections belong to the
publishers, subscribers and admin clients it hands out — each with its own lifetime, thread and
failure mode. A handle that owned them all would eventually close a producer another part of the
application was still using. So this is the one integration where the application still owns real
resources: `kafka.publisher<OrderEvent>()` is yours to close.

**`stx.i18n` also narrows the locale resolver, and that is the half that matters.** WebFlux's
default answers with whatever `Accept-Language` asked for, catalog or no catalog, so a browser
asking for Japanese produces a `Translator` for Japanese that falls back key by key. Told the
supported set, it answers with the closest language actually loaded. It is also the only one of the
seven without `@ConditionalOnClass` — `stx-i18n` is an `exported` dependency of this module, because
the exception handler translates, so the class is always there and the condition could only ever be
true.

**Not every setting is a property, and that is the design.** A `Json`, a `ConnectionFactory` and a
`MongoClientSettings.Builder` are not strings, and growing a key for each one turns a config class
into a worse copy of the thing it configures. Declare your own bean instead —
`@ConditionalOnMissingBean` is on every one of them.

**No buckets are created by `stx.storage`.** `ensureBucket` is one call and belongs to whoever knows
which buckets the application needs. Creating them from a property list would make startup write to
somebody's object store out of a config file nobody reviewed as a schema.

## Dependencies

One rule, borrowed from `stx-ktor`: **every integration dependency is `compile-only`**. An
application that wants the error handling must not inherit Jakarta Validation, a Mongo driver and a
Kafka client along with it. What that means in practice is that a package guarded by
`@ConditionalOnClass` is dark until the application adds that library itself — and that the specs
here list those libraries under `test-dependencies`, or they would assert against conditions that
never match.

That is not theoretical. `ErrorAutoConfigurationTest` found no `ValidationExceptionHandler` on its
first run, because `compile-only` had done exactly what it says — which is what a consumer without
validation sees.

Verify it with `./kotlin show dependencies -m stx-spring`: a compile-only entry is in the COMPILE
scope and absent from RUNTIME.

## Where to read next

| | |
| --- | --- |
| [`../stx-ktor/README.md`](../stx-ktor/README.md) | The same seven backends behind Ktor plugins — the module this one is shaped after |
| [`../stx-i18n/README.md`](../stx-i18n/README.md) | What `Messages` loads, how a key falls back, and what `Accept-Language` negotiation matches |
| [`../../AGENTS.md`](../../AGENTS.md) | The repo's conventions, including publishing and the catalog |
