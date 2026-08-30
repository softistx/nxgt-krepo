# stx-graphix-spring

Spring Boot auto-configuration for `stx-graphix`. Off until `stx.graphix.enabled=true`.

```yaml
stx:
  graphix:
    enabled: true
    path: /graphql
```

```kotlin
@GraphQLController
class OrderMutations(
    private val orders: OrderService,
) {
    @Query
    suspend fun order(id: String): Order? = orders.find(id)

    @Mutation
    suspend fun placeOrder(input: PlaceOrderInput): Order = orders.place(input)
}
```

The application still creates the controller bean (component scan, or an `@Bean`). This module
collects every bean annotated `@GraphQLController` and builds one `Graphix` from them. An
application's own `Graphix` bean wins (`@ConditionalOnMissingBean`).

**A Spring bean is the controller's constructor, not GraphQL context.** `OrderService` is injected
when Boot builds `OrderMutations`. Graphix keeps that instance and the data fetcher calls it.
`@GraphQLContext` is the other bag: who is calling, the locale, anything that exists only for this
operation. The HTTP handler does not yet put `ServerWebExchange` or the security principal in
that map — see [`docs/graphix.md`](../../../docs/graphix.md).

POST and GET share the same JSON envelope as the Ktor plugin. A field error is HTTP 200 plus
`errors[]`. Malformed JSON is HTTP 400. Keys live in
[`docs/spring-configuration.md`](../../../docs/spring-configuration.md); the annotation vocabulary
is [`docs/graphix.md`](../../../docs/graphix.md).
