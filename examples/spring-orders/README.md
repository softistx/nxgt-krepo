# spring-orders

An order book over MongoDB — the smallest thing that shows `stx-spring-boot` end to end, and a
spec-first REST API built the way the [`openapi-spec-first`](../../.agents/skills/openapi-spec-first/SKILL.md)
skill describes.

```
MONGO_URI=mongodb://localhost:27017/orders ./kotlin run -m spring-orders
```

```
GET    /orders?filter=status:eq:PAID&sort=placedAt:DESC&size=20&cursor=…
GET    /orders/valuable?floor=10000
GET    /orders/{id}
POST   /orders
PATCH  /orders/{id}/status
DELETE /orders/{id}
GET    /health
```

**None of those seven lines is written in Kotlin.** They are `openapi/`, and the openapi plugin turns
that document into the models and into the two `@HttpExchange` interfaces the controllers implement.
Every response is `{"data": …, "metadata": …}` and every failure is `{"message": …, "status": …,
"code": …}` in the caller's language — both shapes declared in the document, neither produced by
anything in this module.

## The loop

```
redocly lint orders@v1                                      # the document is valid
redocly bundle orders@v1 -o openapi/api-docs.yaml           # one file, committed
./kotlin build -m spring-orders                             # the interfaces and models follow
```

Bundle before building. `specFile` names `api-docs.yaml`, so an edit under `openapi/` that has not
been re-bundled compiles the previous contract and says nothing about it. `redocly preview-docs
orders@v1` serves the rendered reference while writing.

Adding an endpoint is therefore: a file under `openapi/paths/`, a line in `openapi/openapi.yaml`,
lint, bundle, build — and then the compiler names the method the controller and the service have not
implemented yet.

## What each file is here to show

| | |
| --- | --- |
| `OrdersApplication.kt` | `@SpringBootApplication` and a `main`, and no configuration class at all |
| `resources/application.yaml` | The whole wiring: `stx.*`, one line per feature turned on |
| `openapi/` | The contract. A split document, one file per path and per component; `api-docs.yaml` is redocly's bundle of it |
| `model/Order.kt` | An ordinary document. `@Auditable` is the entire opt-in for the history trail |
| `repository/OrderRepository.kt` | Every Mongo call and nothing else: no base class, the reads and writes as extensions |
| `service/OrderService.kt` | The rules and the `ApiException`s — and it implements the generated interface too |
| `service/OrderEvents.kt` | What the service logs, declared as types — so what is logged is a decision somebody wrote down |
| `rest/OrderController.kt` | A controller with no `@GetMapping` in it: the routing is inherited |
| `rest/HealthController.kt` | The second tag, and why one endpoint gets no service |
| `mapper/OrderMappers.kt` | The document's types on one side, the database's on the other |
| `migration/V1Seed.kt`, `migration/V2Tags.kt` | Two `stx-migrations` migrations: a `@Component`, a declared version, and raw documents |
| `resources/locales/` | Two catalogs. Every key a handler can raise has text in both |
| `testResources/application-test.yaml` | The `test` profile, and the whole of the test bootstrap: a port and a database name |
| `test/ProjectConfig.kt` | One line. The file that makes a Kotest spec a Spring test |
| `test/TestHelper.kt` | What a *generated* client needs on top of `stx-spring-boot`'s factory — and nothing else |
| `test/OrderControllerTest.kt` | The controller end to end, through the interface it implements — one feature per route |
| `test/OrdersApplicationTest.kt` | What a typed client cannot say: the envelope, the translations, the ledger, the audit trail |
| `test/ErrorResponseShapeTest.kt` | That the document's `ErrorResponse` is the one the server actually writes |
| `test/TelemetryCollector.kt` | An `Exporter` bean — the seam an application with a destination of its own uses |
| `test/TelemetryTest.kt` | The spans and logs the application emits, asserted on the signals themselves |

## The things worth reading it for

**A controller implements an interface it did not write.** `OrderController` has no
`@RequestMapping` and no `@GetMapping`: the paths, the verbs and the parameter bindings live on
`IOrdersService`, generated from `openapi/api-docs.yaml`, and Spring reads `@HttpExchange` off an
implemented interface because the mapping searches the whole type hierarchy. `OrderService`
implements the same interface, so the contract holds one layer down as well — which is why the
controller injects the concrete `OrderService` and not the interface: two beans satisfy it.

Two things the interface cannot carry stay on the override. A status other than 200 is
`@ResponseStatus` — the document says 201 for a placed order and 204 for a cancelled one — and the
binding annotations are repeated, because Spring does not reliably inherit parameter annotations and
the failure is a 400 at run time rather than anything at build time.

`coRouter` has not become wrong; it is what this module used to be, and it remains right for
streaming, multipart, and any signature that needs `ServerWebExchange` or `FilePart`. What changed is
the default: when the contract is a document, the interface generated from it is a better place for a
path than a string in a routing DSL.

**The layering is repository → service → controller.** `template` appears in exactly one file. A
service that reaches for it directly ends up expressing a business rule and a query shape in the same
expression, and the rule becomes untestable without a database. The repository throws nothing and
knows no status codes — a missing order is `null`, and what that means to a caller is
`OrderService.get`'s answer.

The stored `OrderStatus` and the generated one stay separate types even though they spell the same
four values. The generated enum carries an `UNKNOWN` sentinel so a client can read a status a newer
server invented, and a sentinel is exactly what must never reach the database — so `mapper/` converts
by name, and an inbound `UNKNOWN` is a 400.

**There is no configuration class.** No `@EnableWebFlux`, no `@ComponentScan` of somebody else's
package, no `WebFluxConfigurer`, no `@RestControllerAdvice`, no `MongoCustomConversions` bean. The
error handler, the locale resolver, the CORS filter, the kotlinx codecs, the `kotlin.time.Instant`
converters, the index creation, the audit trail and the migration gate are auto-configurations that
`application.yaml` switches on. Delete a line from that file and exactly the feature it names goes
away — nothing here starts because a jar is on the classpath.

**A failure is thrown, never returned.** `OrderService.get` throws
`ApiException.notFound("orders.not-found", mapOf("id" to id))` and stops. No handler catches it, no
route maps it to a status, and no `Either` is threaded back up. `ApiExceptionHandler` — registered by
`stx.errors.enabled` — turns it into an `ErrorResponse` whose `message` is looked up in the catalogs
for the request's `Accept-Language` and whose `code` is the untranslated key, because text changes
when somebody improves a sentence and a client matching on text breaks that day.

The throw happens in the service, one layer below the controller, and nothing in between converts
it. `ApiExceptionHandler` is a `@RestControllerAdvice`, so it answers a dispatch error whatever
raised it — and `OrdersApplicationTest` asserts the 404 in English and in French rather than leaving
that to memory. The generated client's own `apiErrorFilter` reads the same response back into the
typed `ErrorResponseException`, which `OrderControllerTest` asserts: the document's account of a
failure is exercised from both ends.

**A page's ordering has exactly one source.** `OrderService.findOrders` reads `filter`, `sort`,
`size` and `cursor` and builds one `MongoPage` from them, and `OrderRepository.page` takes the
finished window. A functional route gets the same thing from a single `request.mongoPage()`; a
controller is handed the four values already bound, so it assembles the window itself.

The tempting spelling — `MongoPage.first(size, query = mongoQuery)` — puts the ordering on the
*query*, where the keyset machinery cannot see it: the first page is right, the second is empty, and
the rest of the collection is unreachable. `MongoPage` refuses it now.
`OrdersApplicationTest` pages through all four orders one at a time and asserts every one is seen exactly once.

**A migration declares its version, and the run is a gate.** `V1Seed` says `override val version = 1L`
— rename the class and nothing changes about what has run. `stx-migrations-spring` collects
`MongoMigration` beans by type, so `@Component` is the whole registration, and the ledger in
`stx_migrations` records each one as `APPLIED` so it never runs again.

The gate is the part worth reading the diff for. This example used to carry a runner that listened for
`ApplicationReadyEvent` and suspended — and Spring does not wait for a suspending listener, so the
port opened while the seed was still being written and every spec here had to poll
`awaitMigrations()` in `beforeSpec` before it could assert anything. `MigrationGate` is an
`InitializingBean`: it runs during the refresh, a migration that throws is a context that does not
start, and the polling is gone from all three specs.

**The migrations write raw `Document`s, not `Order`s.** A migration writes what the database holds,
not what the current mapping says it should hold — so `V1Seed` spells `ref`, which is what
`@Field("ref")` stores `reference` as, and writes a BSON date rather than a string. That the two
agree is a scenario: `OrdersApplicationTest` applies `V1Seed` again into an emptied collection and
reads the result back as `Order`, which is both the round-trip check and a demonstration that running
a migration twice is allowed to be boring.

There is still no `rollback`, for the same reason as before: a migration that needs undoing is undone
by the next one, which is a thing somebody reviewed. [`docs/migrations.md`](../../docs/migrations.md)
is the rest.

**No `stx.mongo` in the yaml, and that is deliberate.** The ledger wants the coroutine driver's
`MongoDatabase`; `stx-spring-boot` bridges one from the `ReactiveMongoDatabaseFactory` Spring Data
already has. Turning on `stx.mongo` would open a second pool against the same server — and, under
test, one that never saw the per-run database suffix, so the migrations would run somewhere else
entirely and every spec would still pass.

**The audit trail is one annotation.** `@Auditable` on `Order` and `stx.data.mongo.audit.enabled` in
the yaml; nothing in `OrderService` mentions it. Every save appends a version to `audits` carrying
the whole state and the properties that changed, and a delete appends a `TERMINAL` entry and closes
the history. The writes happen on a scope of their own, so they land shortly after the response
rather than in it — which is why the spec polls there too.

**The generated interface is also the test client.** `OrderControllerTest` never builds a request:
every scenario calls `IOrdersService` — the very interface `OrderController` implements — over a
client pointed at the running server, so the paths, the verbs, the parameters, the statuses and the
body types are all the document's. One document, one interface, and neither side wrote it, so neither
side can drift from it. Features are named after the route, as in `nxgt-rest`, and a documented
failure is asserted on its status and its `code` rather than on its message, which is translated.

The client is not this module's: `stx-spring-boot` has `httpServiceFactory` and `withClient`, and
`TestHelper` adds only what a *generated* client needs on top — the enum conversion service and the
request-values processor belong to the proxy, the kotlinx codecs and `apiErrorFilter` to the
`WebClient`, and none of the four fails at build time when left out — one factory, then a client
per tag with `withClient`.
`OrdersApplicationTest` keeps a `WebTestClient` for the claims the contract has no name for: the
envelope's JSON shape, the same 404 in two languages, and the 400 from a filter nobody can read.

That client's `WebClient` is configured with the application's own kotlinx codecs rather than left on
Jackson, which is what `models: Kotlinx` costs and buys: the generated classes are `@Serializable`
and `Order.placedAt` is a `kotlin.time.Instant`, a type Jackson has never heard of. The same fact
explains a detail in the document — `ErrorResponse.timestamp` is a plain `type: string` and not
`format: date-time`, because the generated `apiErrorFilter` parses an error body with Jackson even in
the kotlinx style, and a `date-time` there would silently degrade every typed failure to the untyped
`ApiException`.

**`placedAt` is a `kotlin.time.Instant`, and that is the point.** Without
`stx.data.mongo.enabled` the converters are absent and this field fails at *query* time with
`Can't find a codec` — not at insert time, and not at startup. A demo that used
`java.time.Instant` would boot and pass and prove nothing, so this one uses the type that actually
needs the line in the yaml, and `OrdersApplicationTest` writes an order and reads it back to prove it.

`reference` is stored as `ref` for a related reason: a keyset cursor carries the *stored* field
name, so sorting on a renamed property only works because the cursor is built from the mapping
rather than from the property.

**Telemetry is two lines of yaml and one import.** `stx.telemetry.enabled` builds a root and
*installs* it, which is why `OrderService` has a top-level `logger<OrderService>()` and no injected
bean: an installed root is found from anywhere — an `init` block, a `catch`, a class Spring never
built — and with none installed every call is a silent no-op. `stx.telemetry.mongo.enabled` adds the
exporter. Nothing else in this module knows telemetry exists.

The output of the two requests below, from `console: true`:

```
INFO  OrderService  orders.placed  url.path=/orders reference=DEMO-1 total=4200                    [ee0577de/dba175f0]
SPAN  place order  100.264837ms   url.path=/orders reference=DEMO-1 orderId=6a96f22a5c6a7d9ef8d48634 [ee0577de/dba175f0]
SPAN  POST /orders  225.217144ms  http.route=/orders http.response.status_code=201                 [ee0577de/9f7c1517]
WARN  OrderService  orders.reference-taken  url.path=/orders reference=DEMO-1                      [0fe07d1b/194d6c37]
SPAN  place order  12.257243ms ERROR  url.path=/orders reference=DEMO-1                            [0fe07d1b/194d6c37]
SPAN  POST /orders  40.313027ms   http.route=/orders http.response.status_code=409                 [0fe07d1b/100652e2]
```

Four things in it are worth the read.

**The span nests with nothing passed to it.** `place order` and `POST /orders` share a trace id, and
the service's span names the filter's as its parent — `[trace/span]` is the pair on each line. The
span context is a `CoroutineContext.Element` and `stx.telemetry.web-filter` is a `CoWebFilter`, so a
suspending handler is *already inside* the request's span. An MDC gets this wrong in both directions
under WebFlux: it leaks onto whatever else runs on the thread and it is gone after a suspension.

**The server span is named for the route, not the path.** `GET /orders/{id}`, never
`GET /orders/6a96f2…`. The pattern is only known once routing has run, so the filter renames the
span before writing it — one name a backend can group by instead of one per order ever placed. And
the 409 line is not an error: 5xx is this service failing, 4xx is a caller being told no, and
colouring both red makes a dashboard useless. The *inner* span the exception passed through is the
one marked `ERROR`.

**What is logged is a type.** `service/OrderEvents.kt` declares `OrderPlaced`, `OrderStatusChanged`
and `ReferenceTaken`; the serial name becomes the event's name and the fields become the attributes.
So none of those lines carries `customer`, and the reason is not a redaction list somebody has to
keep up to date — it is that `OrderPlaced` does not mention it. `TelemetryTest` asserts that
absence, which is the only way a claim like this stays true.

**One span, deliberately.** Every request already has one; a span per method would time the same work
twice under a second name. `placeOrder` gets one because it is two round trips to Mongo behind one
route and the split between them is worth seeing. The rest of the file logs and does not span.

Where the signals go is a separate decision from any of that. `stx.telemetry.mongo` writes them to a
collection with the field names the JSON-lines format uses — so a Mongo query reads like a `jq`
filter — and retention is a **TTL index**, not a job: Mongo expires the documents itself, on the
primary, whether or not this process is up. The client is telemetry's own and not
`spring.mongodb.uri`, which is the point rather than an oversight: a burst of telemetry must not
exhaust the pool the orders are queueing for.

```js
db.telemetry.findOne({ name: "orders.placed" })
{ _id: …, type: "log", at: ISODate("2026-09-01T15:41:30.285Z"), severity: "Info",
  name: "orders.placed", source: "com.softistx.example.orders.service.OrderService",
  attributes: { "url.path": "/orders", reference: "DEMO-1", total: Long("4200") },
  span: { traceId: "ee0577de…", spanId: "dba175f0…", sampled: true },
  service: "spring-orders", environment: "development" }
```

`at` is a BSON date and not the ISO-8601 string JSON would have given it, because a string is not
something Mongo will expire or index; `total` is a `Long` and not a `Double`, so an exact-match query
on a status code is not a floating-point comparison; and `service` and `environment` are on every
document because a file belongs to one service and a collection does not.

## Running it

The application needs a MongoDB. The workspace's replica set answers on `localhost:27017`, which is
the default in `application.yaml`; `MONGO_URI` overrides it.

```
./kotlin run -m spring-orders
./kotlin test -m spring-orders
```

Telemetry goes to a **second** connection, `TELEMETRY_MONGO_URI`, defaulting to the same server and a
`spring_orders_telemetry` database of its own. Two clients on one server here because a demo has one
server; the separation is what matters, and a deployment points the second somewhere else entirely.
`db.telemetry.find().sort({ at: -1 })` is the whole reader — the documents expire themselves after
seven days.

**The suite runs with the exporter off.** `stx.telemetry` stays enabled under the `test` profile, so
every request a spec makes goes through the same server span the demo runs with — but
`stx.telemetry.mongo.enabled: false`, because that exporter's client is its own and no bean redirects
it: it would write to the `localhost:27017` in `application.yaml` rather than to the container or
`MONGO_TEST_URI` server `MongoTestConfiguration` resolves for everything else. `TelemetryCollector`
takes its place, and is the same seam an application with a destination of its own would use — an
`Exporter` bean, added by the auto-configuration with nothing else configured.

One consequence of the cached context is worth knowing before writing a spec against it: the
collector is *one bean for the whole module*, and a batch lingers up to `stx.telemetry.linger` before
it ships, so signals from the spec that ran before can arrive after `clear()`. Looking one up by name
alone finds whichever request got there first — which is how `TelemetryTest` briefly came to assert
on another spec's 404. Every scenario there now places an order under its own reference and matches
on it.

**The specs start nothing themselves, and this module writes no bootstrap at all.** Each one extends
`MongoSpec` from `stx-spring-boot`'s `com.softistx.spring.testing`, which carries
`@SpringBootTest(webEnvironment = DEFINED_PORT)`, `@ActiveProfiles("test")` and the test beans that
say where MongoDB is; `ProjectConfig` is one line and registers the Kotest extension that makes those
annotations mean something. Spring caches the context, so the application starts once for the module
and the specs clean between scenarios rather than isolating —
`beforeEach { template.clear("orders", "audits") }`, the discipline `nxgt-rest` follows.

MongoDB is the one thing an annotation cannot name, because it is resolved at run time:
`MONGO_TEST_URI` reuses a server that is already up, and otherwise `stx-testing` starts a `mongo:8`
container and stops it when the JVM exits. It arrives as a `MongoConnectionDetails` **bean**, which
Boot's own auto-configuration declares `@ConditionalOnMissingBean` of — so the test one wins, and the
container starts when the first spec that needs one is reached rather than when a file is loaded.
With no Docker and no `MONGO_TEST_URI` the features report skipped rather than failing.

One detail there is worth knowing before writing another Spring spec, and it cost this module a
quiet wrong answer: **Spring Boot 4 renamed the Mongo prefix to `spring.mongodb`.** The driver's URI,
credentials and database are `MongoProperties` under `spring.mongodb`; `spring.data.mongodb` kept only
GridFS and the big-decimal representation. A `spring.data.mongodb.uri` carried over from Boot 3 binds
to nothing and is reported by nothing — the driver falls back to `mongodb://localhost/test`, which on
this machine is the workspace's own replica set and answers happily. That is what these specs were
talking to, container running and unused, while passing. A bean is asked for by type and cannot be
misspelled, which is the argument for `MongoTestConfiguration` over a property in the first place.

Linting and bundling the document needs `@redocly/cli` on the PATH; `redocly.yaml` at the repo root
defines the `orders@v1` alias. `.redocly.lint-ignore.yaml` carries one entry — `/health` has no 4XX
response, because it has no parameters and no body to get wrong — and is generated by
`redocly lint --generate-ignore-file` rather than written.

## What it deliberately does not show

No security — and the document says so as plainly as the code does: it declares no
`securitySchemes`, and `redocly.yaml` turns `security-defined` off for this API with a comment
saying why. Declaring a scheme the application does not enforce would make the document lie, which
is worse than the rule it silences. `stx.security` and the `@PostAuthorize` annotations need an authentication story — a
filter chain, a token format, a user store — and an example that invented one would be teaching that
invention rather than this library. The audit trail records an author of `""` here for the same
reason: nobody is signed in.

No OTLP collector. `stx.telemetry.otlp` is one more key and one more module, and pointing it at a
collector nobody here is running would be a line that looks configured and exports nothing. The Mongo
exporter is the one whose destination this repository's workspace already has.

No `stx-mongo`, `stx-jpa`, `stx-redis`, `stx-kafka`, `stx-amqp` or `stx-storage`. Those are the
`integration/` package — one auto-configuration each, opt-in the same way — and each needs its own
server to demonstrate. `libs/stx-spring-boot/README.md` has the list and
`docs/spring-configuration.md` every key.

---

Apache-2.0 · [Contributing](../../CONTRIBUTING.md) · [All the libraries](../../README.md)
