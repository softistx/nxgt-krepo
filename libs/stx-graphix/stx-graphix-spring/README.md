# stx-graphix-spring

Spring Boot auto-configuration for `stx-graphix`. Off until `stx.graphix.enabled=true`.

```yaml
stx:
  graphix:
    enabled: true
    path: /graphql
    subscriptions: sse   # or graphql-ws
    schema-locations: classpath:graphql/
    sandbox: true        # Apollo Sandbox at /sandbox, off by default
```

```kotlin
@GraphQLController
class OrderMutations(
    private val orders: OrderService,
) {
    @QueryMapping
    suspend fun order(@Argument id: String): Order? = orders.find(id)

    @MutationMapping
    suspend fun placeOrder(@Argument input: PlaceOrderInput): Order = orders.place(input)

    @SubscriptionMapping
    fun orderPlaced(): Flow<Order> = orders.placed

    @BatchMapping
    fun items(orders: List<Order>): Map<Order, List<LineItem>> = this.orders.items(orders)
}
```

The application still creates the controller bean (component scan, or an `@Bean`). This module
collects every bean annotated `@GraphQLController` and builds one `Graphix` from them, plus
`GraphQLScalarType`, `GraphixDirective`, `GraphixCustomizer`, `GraphixInterceptor`,
`GraphQLEngineCustomizer` and `GraphixExceptionHandler` beans. Those arrive as
`ObjectProvider.orderedStream()`, so `@Order` decides which interceptor is outermost and which
exception handler is asked first. An application's own `Graphix` bean wins
(`@ConditionalOnMissingBean`).

**Collection reads the whole bean factory, so a dependency's beans count** — `ObjectProvider` and
`getBeansWithAnnotation` know nothing about which jar a class came from, and a library that
contributes an interceptor or a scalar is collected exactly like a local `@Bean`. What it does not
do is *find* that library's classes: Spring Boot's component scan starts at the
`@SpringBootApplication` package and goes down, so a `@GraphQLController` in
`com.acme.billing.graphql` is invisible to an application rooted at `com.example.shop` until
something registers it — the library ships an auto-configuration (an entry in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`), or the
application adds `@ComponentScan("com.acme.billing")`, `@Import`, or a plain `@Bean`. That is
Spring's rule and this module cannot widen it: a bean that does not exist cannot be collected.

Shipping resolvers in a library for both stacks means carrying both marks — `@GraphQLController`
for Spring, `GraphixResolver` for [`stx-graphix-koin`](../stx-graphix-koin/README.md) — because
Spring can be asked for an annotation and Koin can only be asked for a type.

**A Spring bean is the controller's constructor, not GraphQL context.** `OrderService` is injected
when Boot builds `OrderMutations`. Graphix keeps that instance and the data fetcher calls it.
What exists only for one operation comes the other way: **every operation carries its
`ServerWebExchange`**, over POST, over SSE and over graphql-ws alike.

```kotlin
@Bean
@Order(1)
fun authentication() = GraphixInterceptor {
    put(Caller(exchange.request.headers.getFirst("X-User") ?: "anonymous"))
    proceed()
}

@QueryMapping
fun me(exchange: ServerWebExchange): String = exchange.request.headers.getFirst("X-User") ?: "anonymous"
```

A `GraphixExceptionHandler` bean is what turns a thrown exception into the error a client should
see — the same slot as Spring GraphQL's `@ControllerAdvice` + `@GraphQlExceptionHandler`, as one
function rather than a set of them:

```kotlin
@Component
@Order(1)
class CatalogErrors : GraphixExceptionHandler {
    override suspend fun GraphixErrorScope.handle(failure: Throwable): GraphixError? =
        when (failure) {
            is ProductNotFound -> error.withMessage("No product ${failure.id}").withErrorType(NOT_FOUND)
            else -> null
        }
}
```

Returning `null` means *not mine*, and an exception nobody claims keeps the answer it would have
had.

An interceptor and a handler are beans, not properties, so there is no new `stx.graphix.*` key for
either — see [`docs/graphix.md`](../../../docs/graphix.md) for the reference.

Introspection (`{ __schema }`, `{ __type }`) is on the same path; `stx.graphix.introspection=false`
turns it off.

`stx.graphix.sandbox=true` serves an Apollo Sandbox at `/sandbox`. It is off by default — enabling
GraphQL should not also open an HTML page that advertises the schema — and the page resolves
`/graphql` from the browser's own origin, so it is correct behind a proxy without being told.

POST and GET share the same JSON envelope as the Ktor plugin. A field error is HTTP 200 plus
`errors[]`. Malformed JSON is HTTP 400. Subscriptions default to `text/event-stream`;
`stx.graphix.subscriptions=graphql-ws` is a WebSocket on the same path.
Keys live in
[`docs/spring-configuration.md`](../../../docs/spring-configuration.md); the annotation vocabulary
is [`docs/graphix.md`](../../../docs/graphix.md).
