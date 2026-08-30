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

**Bundle before building.** `spec` names `api-docs.yaml`, so an un-bundled edit compiles the
previous contract and says nothing. The bundle is committed: it is what the generator actually saw.

## Where the document lives

`<module>/openapi/`, beside `module.yaml` — not under `src/`, which holds Kotlin here. `openapi.yaml`
is `$ref` wiring and zero inline schemas, `api-docs.yaml` is redocly's bundle of it, and below those
`paths/` takes one file per URL and `components/{schemas,responses,parameters,security}/` one per
component.

- **A filename is a name.** Redocly rewrites a file `$ref` into `#/components/schemas/<Basename>`,
  so `Order.yaml` *is* the `Order` schema. Components are PascalCase, parameters lowercase, and a
  path file is snake_case with the braces stripped: `/orders/{id}/status` → `orders_id_status.yaml`.
- **A path file's top level is the set of verbs**, so every verb on one URL lives in one file, and
  `$ref` targets are relative and unquoted (`../components/schemas/Order.yaml`, `./PageInfo.yaml`).
- **A module may generate from several documents.** `specs:` is a list; each entry needs a
  `packageName` of its own, and the plugin refuses two that share one.
- **A request body is named after its operation, suffixed `Request`** — `placeOrder` sends
  `PlaceOrderRequest`, `changeStatus` sends `ChangeStatusRequest`. Not after the resource: the body
  belongs to the operation, and two operations on one URL send different shapes.
- **A parameter used twice is a file in `components/parameters/`, `$ref`d per operation.** Never from
  the path item — the generator ignores path-level `parameters` and emits the operation with the
  parameter missing, saying nothing.

`references/document-layout.md` has the tree, the redocly config and a worked file of each kind.

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

`client: Spring` with `interfacePrefix: I` / `interfaceSuffix: Service`, and then four packages and
a mapper, one role each — the layering `nxgt-ktor` and `nxgt-rest` use, minus their hand-written
`port/`, which is generated here as `<packageName>.apis`:

| Package | Holds | Speaks |
| --- | --- | --- |
| `model/` | the `@Document`/`@Entity` and its enums | never reaches the wire |
| `repository/` | `@Repository` over `ReactiveMongoTemplate` — every query, in the stx extensions | domain models |
| `service/` | `@Service … : IOrdersService` — the rules, the `ApiException`s | generated DTOs |
| `rest/` | `@RestController … : IOrdersService` — one-line delegates | generated DTOs |
| `mapper/` | `Order.view()`, `Page<Order>.page()` — pure functions, no Spring | both |

- **`models: Kotlinx` is required, not a preference.** `Auto` gives Jackson for a Spring client, but
  `stx.json.enabled` installs `KotlinSerializationJsonEncoder`/`Decoder` as a `CodecCustomizer`, so
  a model that is not `@Serializable` fails to encode at the first response.
- **A status other than 200 comes from `@ResponseStatus` on the override.** The generated interface
  carries the path, the verb and the parameter bindings; it cannot carry a status.
- **Repeat the binding annotations on the override.** Spring does not reliably inherit parameter
  annotations, and the failure is a 400 at run time rather than anything at build time.
- **Inject the concrete `OrderService`, never `IOrdersService`.** The service implements the same
  interface — that is the point — so two beans satisfy it and by-type injection is ambiguous.
- **The envelope is part of the contract, so it goes in the document.** A controller returns a body,
  not a `ServerResponse`, so `Response<D, M>` and the `.ok()`/`.created()` chain have no place here.
  Declare `OrderPage { data: [Order], metadata: PageInfo }` — it matches `Page` field for field.
- **`ErrorResponse` in the document must match `com.strange.spring.error.ErrorResponse`** —
  `message`, `status`, `code`, `timestamp`, `debugMessage`. Nothing checks it; a round-trip spec does.

**`coRouter` is still right for what a proxy cannot express** — streaming, multipart, and any
signature needing `ServerWebExchange` or `FilePart`. The two styles coexist in one application.

`references/spring-api.md` has the `module.yaml`, the five files written out, and the test wiring.

## E2E tests for a Spring controller

**A controller is tested end to end, through the interface it implements.** The generated `I*Service`
is the client — reused, never re-declared — built with `stx-spring-boot`'s own extensions from
`com.strange.spring.client` against the running application, so the spec speaks over the transport
its callers use. A hand-written client or a mocked service proves neither the routing nor the
contract. It is `nxgt-rest`'s convention; what differs here is that nobody writes the interface.

- **One factory, one client per tag.** `httpServiceFactory(baseUrl, headers) { … }` once, then
  `factory.withClient<IOrdersService>()`, `factory.withClient<IHealthService>()`. A factory per
  interface rebuilds the `WebClient`, and the second interface is where a codec quietly goes missing.
- **The `factory` parameter is the seam a generated client needs**: `registerApiEnumConverters` and
  `apiOperationProcessor` belong to the proxy, the kotlinx codecs and `apiErrorFilter()` to the
  `WebClient`. None of the four fails at build time when left out. Reuse the application's own
  `stxWebJson` bean rather than a `Json` configured nearby.
- **Authentication goes in the `headers` hook**, which runs per request —
  `httpServiceFactory(baseUrl, { it.setBearerAuth(token) })`, as `nxgt-rest` does. `defaultHeaders`
  would pin the first caller's token onto every later call.
- **Let Spring start the application.** `@ActiveProfiles("test")` +
  `@SpringBootTest(webEnvironment = DEFINED_PORT)` on a base spec, `SpringExtension` in
  `io.kotest.provided.ProjectConfig`, and the beans a spec needs in its constructor. No
  `SpringApplicationBuilder`, no context to close, no port to discover — and the context is cached,
  so the application starts once for the module. The base URL is a constant because the port is.
- **The rest of the wiring is one test helper** — the factory, a `WebTestClient`, the cleanup —
  not repeated per spec: `nxgt-rest`'s `helpers/TestHelper.kt`, here `test/TestHelper.kt`.
- **One spec per controller, features named from the generated constants** —
  `feature(Endpoints.POST_ORDERS.label)`, which reads `[POST] /orders` — so a failure names the
  endpoint, the list reads as the surface the document declares, *and* a renamed path fails to
  compile rather than leaving a test named after a route that no longer exists. `.path("42")` fills a
  template for a `WebTestClient` `uri(...)`, and `Endpoints.all` lets a spec assert that no route
  went untested.
- **A documented failure is `shouldThrow<ErrorResponseException>`** on its `status` and its `code`,
  never on the message: `apiErrorFilter()` runs closer to the transport than `httpServiceFactory`'s
  status handler, so the parsed body is there — and the text is translated.
- **Keep a `WebTestClient` for what a typed client cannot say**: the envelope's JSON shape, the
  translated text, a status the document does not declare. In a spec of its own, beside the
  controller specs rather than inside them.

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
  Jackson-bindable: a `format: date-time` becomes a `kotlin.time.Instant` Jackson cannot read, and
  every typed failure degrades to the untyped `ApiException` with nothing said. Keep it to plain
  scalars — `ErrorResponse.timestamp` is a `string`, not a `date-time`, for exactly this reason.
- **A generated `url` has no leading slash and is still right server-side** — the mapping searches
  the type hierarchy and `PathPatternParser` prepends it. `references/spring-api.md` has the proof.

## Where to read

`references/` here is written by hand, not fetched — it holds this repo's own convention, and stays
in step with `examples/spring-orders`, which is the same thing running.

| Topic | File |
| --- | --- |
| The tree, the redocly config, and a worked file of each kind | `references/document-layout.md` |
| The `module.yaml`, the five packages written out, and the test wiring | `references/spring-api.md` |
| What the generator makes of a document — *the file that grows* | `docs/openapi-support.md` |
| Turning the plugin on, and what each `client` needs on the classpath | `plugins/openapi/README.md` |
| The worked example, running | `examples/spring-orders` |
| A generated Spring client, and a Ktor server with its Ktorfit consumer | `examples/demo-spring-client`, `examples/demo-api` + `examples/demo-client` |
