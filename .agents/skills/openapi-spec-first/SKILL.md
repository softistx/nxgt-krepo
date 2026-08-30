---
name: openapi-spec-first
description: Spec-first REST APIs here — a Redocly-split OpenAPI document under <module>/openapi/, models generated as DTOs, and repository → service → controller over the generated @HttpExchange interface. Use when adding or changing a REST endpoint.
---

# Spec-first REST APIs

A REST API here starts as an OpenAPI document, not as Kotlin. `plugins/openapi` turns that document
into the models every consumer needs and, for Spring Boot, into the `@HttpExchange` interface the
controller implements — so the contract cannot drift from the code, because nobody types it twice.

**A shape that is not in the document does not exist.** Adding a field means editing a schema file,
re-bundling and rebuilding; never adding a Kotlin property and describing it afterwards.

## The order of work

```bash
redocly lint orders@v1
redocly bundle orders@v1 -o examples/spring-orders/openapi/api-docs.yaml
./kotlin build -m spring-orders
```

**Bundle before building.** `specFile` names `api-docs.yaml`, so an un-bundled edit compiles the
previous contract and says nothing. The bundle is committed: it is what the generator actually saw.

## Where the document lives

`<module>/openapi/`, beside `module.yaml` — not under `src/`, which holds Kotlin here.

```
openapi/
  openapi.yaml            info, servers, tags, the paths map, securitySchemes — $ref wiring, zero inline schemas
  api-docs.yaml           the bundle: redocly's output, committed, and what specFile names
  paths/                  one file per URL, snake_case, braces stripped: /orders/{id}/status → orders_id_status.yaml
  components/schemas/     one file per component, PascalCase, basename == component name
  components/responses/   PascalCase by HTTP meaning: BadRequest.yaml, Conflict.yaml
  components/parameters/  lowercase, named after the parameter: id.yaml, cursor.yaml
  components/security/    PascalCase scheme name: Bearer.yaml
```

A path file's top level is the set of verbs, so every verb on one URL lives in one file. `$ref`
targets are relative and unquoted — root → `paths/orders_id.yaml`, path file →
`../components/schemas/Order.yaml`, schema → sibling `./PageInfo.yaml`. Redocly rewrites each into
`#/components/schemas/<Basename>` when it bundles, which is why the filename *is* the component name.

## What a name in the document becomes in Kotlin

- **A tag becomes an interface.** `orders-controller` with `interfacePrefix: I` and
  `interfaceSuffix: Service` gives `IOrdersService`. It is the *tag* that is pluralised, not the
  resource — name the tag `order-controller` for `IOrderService`, or set `x-kotlin-name` on it.
- **An `operationId` becomes the function name** *and* the `@ApiOperation` id the generated error
  and auth machinery keys on. Unique, stable, verb-first: `findOrders`, `placeOrder`,
  `changeStatus`. Renaming one breaks every generated client.

Everything else a keyword produces — type mapping, composition, enums, the `x-kotlin-*` extensions,
and what is not handled — is `docs/openapi-support.md`. Read it rather than guess.

## Spring Boot — `stx-spring-boot`, and the controller implements the generated interface

```yaml
plugins:
  openapi:
    enabled: true
    client: Spring
    models: Kotlinx        # NOT the default — see below
    specFile: openapi/api-docs.yaml
    packageName: com.strange.example.orders.api
    interfacePrefix: I
    interfaceSuffix: Service
```

**`models: Kotlinx` is required, not a preference.** `Auto` gives Jackson for a Spring client, but
`stx.json.enabled` installs `KotlinSerializationJsonEncoder`/`Decoder` as a `CodecCustomizer`, so a
model that is not `@Serializable` fails to encode at the first response.

Then four packages and a mapper, one role each — the layering `nxgt-ktor` and `nxgt-rest` use, minus
their hand-written `port/`, which is generated here as `<packageName>.apis`:

| Package | Holds | Speaks |
| --- | --- | --- |
| `model/` | the `@Document`/`@Entity` and its enums | never reaches the wire |
| `repository/` | `@Repository` over `ReactiveMongoTemplate` — every query, in the stx extensions | domain models |
| `service/` | `@Service … : IOrdersService` — the rules, the `ApiException`s | generated DTOs |
| `rest/` | `@RestController … : IOrdersService` — one-line delegates | generated DTOs |
| `mapper/` | `Order.view()`, `Page<Order>.page()` — pure functions, no Spring | both |

```kotlin
@RestController
class OrderController(private val service: OrderService) : IOrdersService {
    @ResponseStatus(HttpStatus.CREATED)
    override suspend fun placeOrder(@RequestBody payload: PlaceOrder) = service.placeOrder(payload)

    @ResponseStatus(HttpStatus.NO_CONTENT)
    override suspend fun cancelOrder(@PathVariable id: String) = service.cancelOrder(id)
}
```

- **A status other than 200 comes from `@ResponseStatus` on the override.** The generated interface
  carries the path, the verb and the parameter bindings; it cannot carry a status.
- **Repeat the binding annotations on the override.** Spring does not reliably inherit parameter
  annotations, and the failure is a 400 at run time rather than anything at build time.
- **Inject the concrete `OrderService`, never `IOrdersService`.** The service implements the same
  interface — that is the point — so two beans satisfy it and by-type injection is ambiguous.
- **The envelope is part of the contract, so it goes in the document.** A controller returns a body,
  not a `ServerResponse`, so `stx-spring-boot`'s `Response<D, M>` and the `.ok()`/`.created()` chain
  have no place here. Declare `OrderPage { data: [Order], metadata: PageInfo }` and return the
  generated type; it matches `com.strange.common.page.Page` field for field.
- **`ErrorResponse` in the document must match `com.strange.spring.error.ErrorResponse`** —
  `message`, `status`, `code`, `timestamp`, `debugMessage`. Nothing checks it; a round-trip spec does.

**`coRouter` is still right for what a proxy cannot express** — streaming, multipart, and any
signature needing `ServerWebExchange` or `FilePart`. The two styles coexist in one application.

## Testing a Spring API

**A spec drives the application through the same interfaces its controllers implement.** A
`WebTestClient` proves the wire shape; a typed client proves the *contract*, and a document change
that neither side followed stops compiling on both at once. `stx-spring-boot`'s `httpServiceFactory`
builds it — its `factory` parameter is the seam a generated client needs:

```kotlin
// `factory` is for the proxy, the trailing lambda for the WebClient. Both are needed:
// registerApiEnumConverters + apiOperationProcessor above, kotlinx codecs + apiErrorFilter below.
val orders: IOrdersService = httpServiceFactory(baseUrl, factory = { … }) { … }.createClient()
```

`examples/spring-orders/test/ApiClients.kt` is that call written out, with the reason each half is
there and why none of it fails at build time.

- **Name a feature after the route** — `feature("POST /orders, through IOrdersService")` — so a
  failure names the endpoint and the list of features reads as the surface the document declares.
  `nxgt-rest` names them the same way.
- **Reuse the application's own `stxWebJson` bean**, not a `Json` configured nearby.
- **Keep a `WebTestClient` for what a typed client hides**: the envelope's JSON shape, the translated
  message text, and a status for a case the document does not declare.
- **`apiErrorFilter()` runs closer to the transport than `httpServiceFactory`'s own status handler**,
  so a documented failure arrives as the generated `ErrorResponseException` carrying a parsed body.
  Leave it out and it is the untyped `ApiException` — right for an internal caller, wrong for a spec
  asserting the document.

## Ktor — `stx-ktor`, and the routes mirror the path files

There is no Ktor *server* emitter — `client: Ktorfit` produces a client interface, which a server
cannot implement. A Ktor server sets `client: None`, uses the generated models as its DTOs and keeps
the same layering with a plain service, one `Route` extension file per tag mirroring `paths/`. A
consumer module takes `client: Ktorfit` off the same document.

## What only surfaces at run time

- **The generated `.utils` package is client machinery, and a server gets it too.** Dead code there,
  and it compiles only because `stx-spring-boot` exports the WebFlux starter, which brings Jackson 3.
- **An error schema is parsed by Jackson even in the kotlinx style.** `apiErrorFilter` takes an
  `ObjectMapper` whatever `models` says, so a schema used by a non-2xx response must stay
  Jackson-bindable: a `format: date-time` in it becomes a `kotlin.time.Instant` that Jackson cannot
  read, and every typed failure degrades to the untyped `ApiException` with nothing said. Keep such
  a schema to plain scalars — `ErrorResponse.timestamp` is a `string`, not a `date-time`, for
  exactly this reason.
- **`@GetExchange(url = "orders/{id}")` has no leading slash** — a client gets its base from
  `WebClient.baseUrl` — and server-side that is still correct. Verified against the spring-webflux
  7.0.8 sources: the mapping reads `@HttpExchange` with `SearchStrategy.TYPE_HIERARCHY`, `url`
  reaches `HttpExchange.value()` through a mutual `@AliasFor`, and `RequestMappingInfo` puts every
  pattern through `PathPatternParser.initFullPathPattern`, which prepends the `/`.

## Where to read

| Topic | File |
| --- | --- |
| What the generator makes of a document — *the file that grows* | `docs/openapi-support.md` |
| Turning the plugin on, and what each `client` needs on the classpath | `plugins/openapi/README.md` |
| The worked example: split document, controllers, typed-client spec | `examples/spring-orders` |
| A generated Spring client, and a Ktor server with its Ktorfit consumer | `examples/demo-spring-client`, `examples/demo-api` + `examples/demo-client` |
