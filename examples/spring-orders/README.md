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
| `rest/OrderController.kt` | A controller with no `@GetMapping` in it: the routing is inherited |
| `rest/HealthController.kt` | The second tag, and why one endpoint gets no service |
| `mapper/OrderMappers.kt` | The document's types on one side, the database's on the other |
| `migration/V1Seed.kt`, `migration/V2Tags.kt` | Two migrations, and how the class name becomes the version |
| `resources/locales/` | Two catalogs. Every key a handler can raise has text in both |
| `testResources/application-test.yaml` | The `test` profile, and the whole of the test bootstrap: a port and a database name |
| `test/ProjectConfig.kt` | One line. The file that makes a Kotest spec a Spring test |
| `test/TestHelper.kt` | What a *generated* client needs on top of `stx-spring-boot`'s factory — and nothing else |
| `test/OrderControllerTest.kt` | The controller end to end, through the interface it implements — one feature per route |
| `test/OrdersApplicationTest.kt` | What a typed client cannot say: the envelope, the translations, the migrations, the audit trail |
| `test/ErrorResponseShapeTest.kt` | That the document's `ErrorResponse` is the one the server actually writes |

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
converters, the index creation, the audit trail and the migration runner are auto-configurations that
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

**A migration is a class whose name is its version.** `V1Seed` is order 1, `V2Tags` is order 2, and
the runner records each in `migrations` on success so it never runs again. Two things follow that are
easy to get wrong: there is no `rollback` — a migration that needs undoing is undone by the next one,
which is a thing somebody reviewed — and the runner is **not a startup gate**. It listens for
`ApplicationReadyEvent` and suspends, and Spring does not wait for a suspending listener, so the
records appear shortly after the port opens rather than before it. `awaitMigrations()` polls for them in
`beforeSpec` for exactly that reason: the seed would otherwise land in the middle of whichever
scenario went first.

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

## Running it

The application needs a MongoDB. The workspace's replica set answers on `localhost:27017`, which is
the default in `application.yaml`; `MONGO_URI` overrides it.

```
./kotlin run -m spring-orders
./kotlin test -m spring-orders
```

**The specs start nothing themselves, and this module writes no bootstrap at all.** Each one extends
`MongoSpec` from `stx-spring-boot`'s `com.strange.spring.testing`, which carries
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

No `stx-mongo`, `stx-jpa`, `stx-redis`, `stx-kafka`, `stx-amqp` or `stx-storage`. Those are the
`integration/` package — one auto-configuration each, opt-in the same way — and each needs its own
server to demonstrate. `libs/stx-spring-boot/README.md` has the list and
`docs/spring-configuration.md` every key.
