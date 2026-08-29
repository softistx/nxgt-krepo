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
```

More packages arrive in their own changes: the Spring Data Reactive Mongo layer, and one
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

`origins` is empty by default, because a browser policy that arrives already permitting somebody is
the wrong shape of default. Everything else defaults permissively — once an origin is trusted,
restricting which methods it may use adds nothing an attacker at that origin cannot work around.

**One combination fails at startup on purpose.** `origins: ["*"]` with `allow-credentials: true` is
forbidden by the CORS specification, and Spring throws when the *request* arrives rather than when
the bean is built — which turns a configuration mistake into an intermittent browser failure found by
whoever is testing the front end. This refuses it while the context is starting and names
`origin-patterns`, which is what actually does the job: the concrete requesting origin is echoed back
rather than a wildcard.

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
