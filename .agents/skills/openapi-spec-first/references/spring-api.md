# A Spring API from the document

The five files `examples/spring-orders` is made of, written out. Written by hand, not fetched.

## Turning it on

```yaml
# examples/spring-orders/module.yaml
plugins:
  openapi:
    enabled: true
    client: Spring
    # Not `Auto`, which would give Jackson: `stx.json.enabled` puts kotlinx codecs on WebFlux, so a
    # model that is not `@Serializable` fails to encode at the first response.
    models: Kotlinx
    specFile: openapi/api-docs.yaml
    packageName: com.strange.example.orders.api
    # `orders-controller` -> `IOrdersService`, `health-controller` -> `IHealthService`.
    interfacePrefix: I
    interfaceSuffix: Service
```

Nothing is written to `packageName` itself. Interfaces land in `<packageName>.apis`, schemas in
`.models`, and the client machinery in `.utils`. A Spring server needs no dependency beyond what
`stx-spring-boot` already exports.

## What the generator emits

Abridged — three of the six operations, and `findOrders` with two of its four parameters.

```kotlin
@HttpExchange
public interface IOrdersService {
  @GetExchange(url = "orders/{id}")
  @ApiOperation(id = "findOrder")
  public suspend fun findOrder(@PathVariable(name = "id") id: String): OrderResponse

  @PostExchange(url = "orders", contentType = "application/json")
  @ApiOperation(id = "placeOrder")
  public suspend fun placeOrder(@RequestBody body: PlaceOrder): OrderResponse

  @GetExchange(url = "orders")
  @ApiOperation(id = "findOrders")
  public suspend fun findOrders(
    @RequestParam(name = "filter", required = false) filter: String? = null,
    @RequestParam(name = "size", required = false) size: Int? = null,
  ): OrderPage
}
```

The paths have no leading slash because a client gets its base from `WebClient.baseUrl`;
`PathPatternParser.initFullPathPattern` prepends one server-side. `required = false` is there
because nullability alone does not stop Spring's argument resolver throwing on a null.

## rest/ — the controller

**A generated `url` has no leading slash, and the mapping is still correct.** `@GetExchange(url =
"orders/{id}")` is written that way because a client gets its base from `WebClient.baseUrl`. Verified
against the spring-webflux 7.0.8 sources: `RequestMappingHandlerMapping` reads `@HttpExchange` with
`SearchStrategy.TYPE_HIERARCHY`, so an interface a `@RestController` implements is found; `url`
reaches `HttpExchange.value()` through a mutual `@AliasFor`; and `RequestMappingInfo` puts every
pattern through `PathPatternParser.initFullPathPattern`, which prepends the `/`. A 404 on every route
after wiring a controller this way means one of those three is not what it was.


No `@RequestMapping` and no `@GetMapping`: the routing is inherited. What stays is the two things
the interface cannot carry.

```kotlin
@RestController
class OrderController(
    private val service: OrderService,
) : IOrdersService {
    override suspend fun findOrders(
        @RequestParam filter: String?,
        @RequestParam sort: String?,
        @RequestParam size: Int?,
        @RequestParam cursor: String?,
    ) = service.findOrders(filter, sort, size, cursor)

    @ResponseStatus(HttpStatus.CREATED)
    override suspend fun placeOrder(
        @RequestBody body: PlaceOrder,
    ) = service.placeOrder(body)

    @ResponseStatus(HttpStatus.NO_CONTENT)
    override suspend fun cancelOrder(
        @PathVariable id: String,
    ) = service.cancelOrder(id)
}
```

It takes the concrete `OrderService`, not `IOrdersService`: the service implements the same
interface, so two beans satisfy it and by-type injection is ambiguous.

An endpoint with nothing to delegate to needs no service — `HealthController` answers directly, and
gains one the moment health means a ping to Mongo rather than a constant.

## service/ — the rules, and the same interface

```kotlin
@Service
class OrderService(
    private val orders: OrderRepository,
) : IOrdersService {
    override suspend fun findOrders(filter: String?, sort: String?, size: Int?, cursor: String?): OrderPage =
        orders
            .page(
                MongoPage.first(
                    size = size?.takeIf { it > 0 } ?: DEFAULT_PAGE_SIZE,
                    cursor = cursor,
                    query = filter.parseFilter(),
                    sort = sort.parseSort().toSort(),
                ),
            ).page()

    override suspend fun placeOrder(body: PlaceOrder): OrderResponse {
        if (orders.existsByReference(body.reference)) {
            throw ApiException.conflict(KEY_REFERENCE_TAKEN, mapOf("reference" to body.reference))
        }
        return orders.insert(body.toEntity()).response()
    }

    /** Not on the interface: the document describes an HTTP surface, and this is not part of one. */
    private suspend fun get(id: String): Order =
        orders.find(id) ?: throw ApiException.notFound(KEY_ORDER_NOT_FOUND, mapOf("id" to id))
}
```

**The four query parameters become one `MongoPage` here.** The ordering has to reach `MongoPage.sort`
and not the `Query`, or the cursor and the rows disagree — a right-looking first page and an empty
second one. A functional route gets the same window from `request.mongoPage()`; a controller is
handed the values already bound, so the service assembles it.

`nxgt-rest` puts a non-HTTP method on the port with a `TODO()` default body. A generated interface
leaves no room for that, which is the better outcome: it belongs on the service.

## repository/ — every query, and nothing else

```kotlin
@Repository
class OrderRepository(
    private val template: ReactiveMongoTemplate,
) {
    suspend fun page(window: MongoPage): Page<Order> = template.findPage(window)

    suspend fun find(id: String): Order? = template.findById<Order>(id).awaitFirstOrNull()

    suspend fun existsByReference(reference: String): Boolean = template.existsBy<Order>(("ref" eq reference).query)

    suspend fun insert(order: Order): Order = template.insert(order).awaitSingle()
}
```

It throws nothing and knows no status codes: a missing order is `null`, and what that means to a
caller is the service's answer. It is a plain class, not a `CrudRepository` — `stx-spring-boot`
offers extensions rather than a base class, because `findPage<Order>(window)` needs no `KClass` and
a class cannot have a `reified` type parameter.

## mapper/ — the two vocabularies meeting

Abridged; the real mappers carry every field.

```kotlin
fun Order.view(): ApiOrder =
    ApiOrder(id = id, reference = reference, status = ApiOrderStatus.valueOf(status.name), placedAt = placedAt)

fun Order.response(): OrderResponse = OrderResponse(data = view())

fun Page<Order>.page(): OrderPage =
    OrderPage(data = data.map { it.view() }, metadata = ApiPageInfo(startCursor = info.startCursor, /* … */))

/** `null` for a value this service cannot name — an inbound `UNKNOWN` is a 400, not a crash. */
fun ApiOrderStatus.toDomain(): OrderStatus? =
    if (this == ApiOrderStatus.UNKNOWN) null else OrderStatus.valueOf(name)
```

**The stored enum and the generated one stay separate types** even when they spell the same values.
The generated one carries an `UNKNOWN` sentinel so a client can read a status a newer server
invented, and a sentinel is exactly what must never reach the database.

## test/ — e2e through the generated clients

**The client extensions are the library's.** `com.strange.spring.client` already has
`httpServiceFactory`, `httpClient<T>()` and `withClient<T>()`; a spec that assembled its own
`WebClient` would be asserting against a transport no caller uses. What a *generated* client adds on
top is four settings, and none of them fails at build time when left out.

The application is Spring's to start: `$libs.kotest.extensions.spring` in `test-dependencies`, a
`test` profile under `testResources/`, and a base spec carrying the annotations.

```kotlin
// test/OrdersSpec.kt — the bootstrap, once, for every spec in the module
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
abstract class OrdersSpec(body: FeatureSpec.() -> Unit) : FeatureSpec(body) {
    companion object {
        const val BASE_URL = "http://localhost:8088"          // `server.port` in the test profile
        val mongo = mongoContainer()                          // declared; nothing started yet

        // Read while the context is being built — so the container starts when a spec that needs
        // one is reached, and outranks every property source, including `application.yaml`.
        @JvmStatic
        @DynamicPropertySource
        fun mongoUri(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri") { mongo.endpoint?.withDatabase(DATABASE) ?: UNREACHABLE }
        }
    }
}

// test/ProjectConfig.kt — in package io.kotest.provided, where Kotest looks for it
object ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(SpringExtension())
}

// test/TestHelper.kt — the client wiring, and nothing about starting anything
fun apiFactory(json: Json, baseUrl: String = OrdersSpec.BASE_URL, headers: (HttpHeaders) -> Unit = {}) =
    httpServiceFactory(
        baseUrl,
        headers,                                   // per request: `{ it.setBearerAuth(token) }`
        factory = {
            conversionService(DefaultFormattingConversionService().also(::registerApiEnumConverters))
            httpRequestValuesProcessor(apiOperationProcessor())
        },
    ) {
        codecs {
            it.defaultCodecs().kotlinSerializationJsonDecoder(KotlinSerializationJsonDecoder(json))
            it.defaultCodecs().kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(json))
        }
        filter(apiErrorFilter())
    }
```

```kotlin
// test/OrderControllerTest.kt — one spec per controller, features named after the route
class OrderControllerTest(
    template: ReactiveMongoTemplate,                // autowired: Spring built the context
    json: Json,                                     // the application's own `stxWebJson`
) : OrdersSpec({
    val orders = apiFactory(json).withClient<IOrdersService>()   // one client per tag, off one factory

    beforeSpec { template.awaitMigrations() }
    beforeEach { template.clean() }                 // each scenario writes what it reads

    feature("POST /orders") {
        scenario("a placed order comes back with the id it was given") {
            orders.placeOrder(PlaceOrder(reference = "C-3001", customer = "lovelace", total = 12_000))
                .data.status shouldBe OrderStatus.PENDING
        }

        scenario("a reference already taken is the 409 the document declares") {
            orders.placeOrder(PlaceOrder(reference = "C-3001", customer = "lovelace", total = 12_000))

            shouldThrow<ErrorResponseException> {
                orders.placeOrder(PlaceOrder(reference = "C-3001", customer = "someone else", total = 1))
            }.error.code shouldBe "orders.reference-taken"
        }
    }
})
```

Nothing in that spec builds a request: the paths, the verbs, the parameters, the statuses and the
body types are all the document's. Assert a failure on `status` and `code`, never on the message —
the text is translated and changes the day somebody improves a sentence.

The four settings, and what each one silently costs:

| Setting | Left out |
| --- | --- |
| kotlinx codecs, from the app's own `stxWebJson` | a generated `placedAt` is a `kotlin.time.Instant`, which Jackson has never heard of |
| `registerApiEnumConverters` | Spring writes an enum with `Enum.name()` and never consults `toString()`: `IN_PROGRESS` where the document says `in-progress` |
| `apiOperationProcessor` | by the time a `ClientRequest` exists the method is gone, so `apiErrorFilter` cannot tell which operation failed |
| `apiErrorFilter()` | a documented failure arrives as the untyped `ApiException`, not `ErrorResponseException` with a parsed body |

## Where each kind of assertion belongs

| Claim | Spec | Driven by |
| --- | --- | --- |
| The contract: types, statuses, typed failures | `<X>ControllerTest` | the generated interface as a client |
| The wire: the envelope's JSON shape, `$.data.length()` | `<App>ApplicationTest` | `WebTestClient` |
| Translation: the same 404 in English and in French | `<App>ApplicationTest` | `WebTestClient`, with `Accept-Language` |
| A case the document does not declare | `<App>ApplicationTest` | `WebTestClient` — a typed client has no name for it |

**A per-run database goes in the URI.** `spring.data.mongodb.database` is read only while Boot is
building a connection string from `host`/`port`, so once `spring.data.mongodb.uri` is set it is
ignored, silently, and every run shares one database while looking isolated — including the one a
`./kotlin run` writes to.

**A container starts when a spec needs it, and not before.** `ContainerService.endpoint` is a `lazy`,
so `mongoContainer()` at the top of a file starts nothing; the first read is the
`@DynamicPropertySource` supplier, which Spring evaluates while building the context for the first
spec that runs. Resolve it any earlier — in `beforeProject`, or in a property initialiser that
connects — and a run whose specs are all skipped still pays for a container.

**Wait for what starts after the port opens.** `MigrationRunner` listens for `ApplicationReadyEvent`
and suspends, and Spring does not wait for a suspending listener — so a seed lands shortly *after*
the server is up, and lands in the middle of whichever scenario went first if nothing waits.
`beforeSpec { template.awaitMigrations() }` is the gate; `beforeEach { template.clean() }` is the
discipline that makes each scenario name its own state, as `nxgt-rest`'s `cleanUp()` does.

**One context, cached, for every spec that shares the configuration.** Spring's test context cache
keys on the annotations, so two specs annotated alike get one application — which is why the specs
clean rather than isolate, and why a spec must not depend on another having run.
