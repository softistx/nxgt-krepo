# stx-graphql-spring

Spring Boot auto-configuration for `stx-graphql`. Off until `stx.graphql.enabled=true`.

```yaml
stx:
  graphql:
    enabled: true
    path: /graphql
```

```kotlin
@GraphQLController
class ProductQueries(private val store: ProductStore) {
    @Query
    suspend fun product(id: String): Product? = store.find(id)
}
```

The application still creates the controller bean (component scan, or an `@Bean`). This module
collects every bean annotated `@GraphQLController` and builds one `Graphix` from them. An
application's own `Graphix` bean wins (`@ConditionalOnMissingBean`).

POST and GET share the same JSON envelope as the Ktor plugin. A field error is HTTP 200 plus
`errors[]`. Malformed JSON is HTTP 400. Keys live in
[`docs/spring-configuration.md`](../../../docs/spring-configuration.md); the annotation vocabulary
is [`docs/graphql.md`](../../../docs/graphql.md).
