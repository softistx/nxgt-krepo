# stx-spring-boot

The Spring Boot seam — the same job `stx-ktor` does for Ktor, and the same shape: what belongs to
the framework rather than to any one library, one package per concern.

**`io.github.softistx:stx-spring-boot`** — [how to depend on it](../../../docs/consuming.md).

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

An application needs nothing beside `//libs/core/stx-spring-boot` to use any of this. The one dependency
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
[`docs/spring-configuration.md`](../../../docs/spring-configuration.md) — the other half that grows a
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

Extensions over a functional route's request and response — paging, sort, validated bodies, the `Response` envelope. The decisions behind them (a bad paging parameter is not a failure, `sort` is not Spring Data's `Sort`, no `error` field) are in [`docs/spring-web.md`](../../../docs/spring-web.md#routes).

## Calling another service

`httpClient<T>(baseUrl)` and `httpServiceFactory`/`generatedApiFactory` build a typed client from an `@HttpExchange` interface, and an error response arrives as an `ApiException`. The details are in [`docs/spring-web.md`](../../../docs/spring-web.md#calling-another-service).

## What is not here

**Reactor await extensions.** `kotlinx-coroutines-reactor` and `kotlinx-coroutines-reactive` already
have them, and a second set of shorter names over the same functions is a vocabulary to learn rather
than a capability to use. The one thing worth knowing is which artifact a given name is in:
`awaitSingleOrNull` on a `Mono` is the Reactor one, `awaitFirstOrNull` on a `Publisher` is the other,
and this module depends on both.

## Who is calling

`currentUser()` / `requireCurrentUser()` read the *reactive* security context, and `@RequireRole`, `@RequireAuthority`, `@RequireOwnership` and `@FilterByOwnership` say who may call what. Why each is `@PreAuthorize` or post-hoc, and the two things that must be switched on: [`docs/spring-web.md`](../../../docs/spring-web.md#who-is-calling).

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

**Two mistakes fail at startup on purpose.** An `origins` entry without its scheme (`localhost:5173`
for `http://localhost:5173`) would match nothing in a browser, so it is refused. And `origins: ["*"]`
with `allow-credentials: true` is forbidden by the CORS specification, and Spring throws when the *request* arrives rather than when
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

Pass `allowed = setOf(...)` to `mongoPage` / `mongoQuery(allowed)` to restrict the fields a client may name;
unset, `?filter=passwordHash:like:a` is an oracle — see "Which fields a client may name" in the doc below.

The vocabulary — every operator in both forms, the filter tokens and how each reads its value, the
sort grammar, and the three rules a keyset cursor has to obey — is
[`docs/spring-mongo-queries.md`](../../../docs/spring-mongo-queries.md). It gains an entry every phase,
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

The keys are in [`docs/spring-configuration.md`](../../../docs/spring-configuration.md); the ordering is
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

`@Auditable` on a Mongo document appends an `AuditEntry` per save and delete, written asynchronously and never updated. Setup, the per-document opt-in, `TERMINAL` deletes, the asynchronous write and the collection-name decision: [`docs/spring-mongo-queries.md`](../../../docs/spring-mongo-queries.md#the-audit-trail).

## Migrations — see `libs/data/stx-migrations`

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
[`docs/migrations.md`](../../../docs/migrations.md) is the vocabulary.

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
| `stx.mongo` | `libs/data/stx-mongo/stx-mongo-spring` |
| `stx.jpa` | `libs/data/stx-jpa/stx-jpa-spring` |
| `stx.redis` | `libs/data/stx-redis/stx-redis-spring` |
| `stx.kafka` | `libs/messaging/stx-kafka/stx-kafka-spring` |
| `stx.amqp` | `libs/messaging/stx-amqp/stx-amqp-spring` |
| `stx.storage` | `libs/data/stx-storage/stx-storage-spring` |
| `stx.i18n` | `libs/api/stx-i18n/stx-i18n-spring` |

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

`com.softistx.spring.testing` lives in `src/` because it is for consumers: a `ProjectConfig`, a spec extending `MongoSpec`, and an `application-test.yaml`. The surface and the reasons behind it (Mongo as a bean, the run's database, the port) are in [`docs/spring-testing.md`](../../../docs/spring-testing.md).

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
| [`../../docs/spring-mongo-queries.md`](../../../docs/spring-mongo-queries.md) | What a query may say — the operators, the filter and sort grammars, and the paging rules |
| [`../../docs/spring-configuration.md`](../../../docs/spring-configuration.md) | Every `stx.*` key, its default, and what switching it on costs |
| [`docs/spring-web.md`](../../../docs/spring-web.md) | Routes, typed clients for other services, and the annotations that say who is calling |
| [`docs/spring-testing.md`](../../../docs/spring-testing.md) | What an application's specs write, and how the harness finds Mongo, the database and the port |
| [`../stx-ktor/README.md`](../stx-ktor/README.md) | The Ktor seam — the same split, drawn the same way, and the resource idiom its plugins are built on |
| [`../stx-i18n/stx-i18n/README.md`](../../api/stx-i18n/stx-i18n/README.md) | What `Messages` loads, how a key falls back, and what `Accept-Language` negotiation matches |
| [`../../AGENTS.md`](../../../AGENTS.md) | The repo's conventions, including publishing and the catalog |

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
