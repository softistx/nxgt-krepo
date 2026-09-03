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
`GraphQLScalarType`, `GraphixDirective`, `GraphixCustomizer`, `GraphixInterceptor` and
`GraphQLEngineCustomizer` beans. Those arrive as `ObjectProvider.orderedStream()`, so `@Order`
decides which interceptor is outermost. An application's own `Graphix` bean wins
(`@ConditionalOnMissingBean`).

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

An interceptor is a bean, not a property, so there is no new `stx.graphix.*` key for it — see
[`docs/graphix.md`](../../../docs/graphix.md) for the reference.

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
