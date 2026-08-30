# graphix-shop

A shop catalogue over GraphQL — the smallest thing that shows `stx-graphix-ktor` end to end.

```
./kotlin run -m graphix-shop
```

```
POST /graphql
{ "query": "{ products { name price } }" }

POST /graphql
{ "query": "mutation { addProduct(name: \"Anvil\", price: 9000) { id name } }" }

POST /graphql
{ "query": "subscription { productAdded { name price } }" }
```

The subscription is `text/event-stream` on the same path.

In-memory, no database. The point is the plugin: `install(GraphQL) { schema { query(catalog); mutation(catalog); subscription(catalog) } }`.
`Catalog` is both the store and the root: mutations call methods on the same instance the queries
and subscriptions do. A Spring app would put `OrderService` on the controller constructor instead;
that is still not GraphQL context. [`docs/graphix.md`](../../docs/graphix.md) draws the line.
