# What the stx-spring-boot web layer may say

Routes, typed clients for other services, and the annotations that say who is calling. This is the
usage half of `libs/core/stx-spring-boot`; the reasoning behind the module's shape is in
[its README](../libs/core/stx-spring-boot/README.md), and every `stx.*` key is in
[`spring-configuration.md`](spring-configuration.md).

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
  its own file for the `compile-only` reason in the [README's Dependencies section](../libs/core/stx-spring-boot/README.md#dependencies).

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
is carried across, so its 404 is a 404 here rather than a 500. A status `HttpStatus` does not list
(Cloudflare's 522, nginx's 499) maps to 502 for an unknown 5xx, 400 for an unknown 4xx and 500
otherwise.

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

## Who is calling

```kotlin
val owner = requireCurrentUser().username
```

**`ReactiveSecurityContextHolder`, never `SecurityContextHolder`.** The non-reactive holder is a
`ThreadLocal`, and in WebFlux a request is not a thread — the same trap
[the request's locale](../libs/core/stx-spring-boot/README.md#the-requests-locale) describes, in the other half of the stack. `currentUser()` returns null for an anonymous request,
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

