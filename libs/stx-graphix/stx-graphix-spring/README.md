# stx-graphix-spring

Spring Boot auto-configuration for `stx-graphix`. Off until `stx.graphix.enabled=true`.

```yaml
stx:
  graphix:
    enabled: true
    path: /graphql
    subscriptions: sse   # or graphql-ws
    schema-locations: classpath:graphql/
```

```kotlin
@GraphQLController
class OrderMutations(
    private val orders: OrderService,
) {
    @QueryMapping
    suspend fun order(id: String): Order? = orders.find(id)

    @MutationMapping
    suspend fun placeOrder(input: PlaceOrderInput): Order = orders.place(input)

    @SubscriptionMapping
    fun orderPlaced(): Flow<Order> = orders.placed

    @BatchMapping
    fun items(orders: List<Order>): Map<Order, List<LineItem>> = this.orders.items(orders)
}
```

The application still creates the controller bean (component scan, or an `@Bean`). This module
collects every bean annotated `@GraphQLController` and builds one `Graphix` from them, plus
`GraphQLScalarType`, `GraphixDirective`, `GraphixCustomizer` and `GraphQLEngineCustomizer`
beans. An application's own `Graphix` bean wins (`@ConditionalOnMissingBean`).

**A Spring bean is the controller's constructor, not GraphQL context.** `OrderService` is injected
when Boot builds `OrderMutations`. Graphix keeps that instance and the data fetcher calls it.
`@GraphQLContext` is the other bag: who is calling, the locale, anything that exists only for this
operation. The HTTP handler does not yet put `ServerWebExchange` or the security principal in
that map — see [`docs/graphix.md`](../../../docs/graphix.md).

Introspection (`{ __schema }`, `{ __type }`) is on the same path.

POST and GET share the same JSON envelope as the Ktor plugin. A field error is HTTP 200 plus
`errors[]`. Malformed JSON is HTTP 400. Subscriptions default to `text/event-stream`;
`stx.graphix.subscriptions=graphql-ws` is a WebSocket on the same path.
Keys live in
[`docs/spring-configuration.md`](../../../docs/spring-configuration.md); the annotation vocabulary
is [`docs/graphix.md`](../../../docs/graphix.md).
