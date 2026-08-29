# spring-orders

An order book over MongoDB — the smallest thing that shows `stx-spring` end to end.

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

Every response is `{"data": …, "metadata": …}`; every failure is `{"message": …, "status": …,
"code": …}` in the caller's language. Nothing in this module produces either shape — see below.

## What each file is here to show

| | |
| --- | --- |
| `OrdersApplication.kt` | `@SpringBootApplication` and a `main`, and no configuration class at all |
| `resources/application.yaml` | The whole wiring: `stx.*`, one line per feature turned on |
| `domain/Order.kt` | An ordinary document. `@Auditable` is the entire opt-in for the history trail |
| `domain/OrderService.kt` | One class over `ReactiveMongoTemplate`: no repository, no base class, the reads and writes as extensions |
| `routes/OrderRoutes.kt` | Functional routes with no `try`/`catch` and no response building |
| `model/Payloads.kt` | The wire types, and why the response view is not the document |
| `migration/V1Seed.kt`, `migration/V2Tags.kt` | Two migrations, and how the class name becomes the version |
| `resources/locales/` | Two catalogs. Every key a route can raise has text in both |
| `test/OrdersTest.kt` | The whole application over HTTP against a real MongoDB |

## The six things worth reading it for

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

That it works from a `RouterFunction` at all is worth stating, because a `@RestControllerAdvice`
reads as a thing for annotated controllers. It is not — on WebFlux the advice answers a dispatch
error whatever handler raised it. `OrdersTest` asserts the 404 in English and in French rather than
leaving that to memory, since a library whose `web/` package is entirely functional and whose error
handling is entirely an advice depends on it.

**A page's ordering has exactly one source.** `request.mongoPage()` reads `?filter=`, `?sort=`,
`?size=` and `?cursor=` and puts each where it belongs, and `OrderService.page` takes the finished
window. The tempting spelling — `MongoPage.first(size, query = request.mongoQuery)` — puts the
ordering on the *query*, where the keyset machinery cannot see it: the first page is right, the
second is empty, and the rest of the collection is unreachable. `MongoPage` refuses it now.
`OrdersTest` pages through all four orders one at a time and asserts every one is seen exactly once.

**A migration is a class whose name is its version.** `V1Seed` is order 1, `V2Tags` is order 2, and
the runner records each in `migrations` on success so it never runs again. Two things follow that are
easy to get wrong: there is no `rollback` — a migration that needs undoing is undone by the next one,
which is a thing somebody reviewed — and the runner is **not a startup gate**. It listens for
`ApplicationReadyEvent` and suspends, and Spring does not wait for a suspending listener, so the
records appear shortly after the port opens rather than before it. `OrdersTest` polls for them for
exactly that reason.

**The audit trail is one annotation.** `@Auditable` on `Order` and `stx.data.mongo.audit.enabled` in
the yaml; nothing in `OrderService` mentions it. Every save appends a version to `audits` carrying
the whole state and the properties that changed, and a delete appends a `TERMINAL` entry and closes
the history. The writes happen on a scope of their own, so they land shortly after the response
rather than in it — which is why the spec polls there too.

**`placedAt` is a `kotlin.time.Instant`, and that is the point.** Without
`stx.data.mongo.enabled` the converters are absent and this field fails at *query* time with
`Can't find a codec` — not at insert time, and not at startup. A demo that used
`java.time.Instant` would boot and pass and prove nothing, so this one uses the type that actually
needs the line in the yaml, and `OrdersTest` reads the seeded orders back to prove it.

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

`OrdersTest` needs no server of its own: `MONGO_TEST_URI` reuses one that is already up, and
otherwise `stx-testing` starts a `mongo:8` container for the run and stops it afterwards. Every
scenario runs against a database of its own, dropped when the spec ends — the server is usually
somebody else's, and a run that reuses one has to leave it as it found it. With no Docker and no
`MONGO_TEST_URI` the spec reports skipped rather than failing.

## What it deliberately does not show

No security. `stx.security` and the `@PostAuthorize` annotations need an authentication story — a
filter chain, a token format, a user store — and an example that invented one would be teaching that
invention rather than this library. The audit trail records an author of `""` here for the same
reason: nobody is signed in.

No `stx-mongo`, `stx-jpa`, `stx-redis`, `stx-kafka`, `stx-amqp` or `stx-storage`. Those are the
`integration/` package — one auto-configuration each, opt-in the same way — and each needs its own
server to demonstrate. `libs/stx-spring/README.md` has the list and
`docs/spring-configuration.md` every key.
