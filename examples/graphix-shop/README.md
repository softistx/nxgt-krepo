# graphix-shop

A shop catalogue over GraphQL — the smallest thing that shows `stx-graphix-ktor` end to end.

```
./kotlin run -m graphix-shop
```

```
POST /graphql
{ "query": "{ products { name price reviews { body } } }" }

POST /graphql
{ "query": "mutation { addProduct(name: \"Anvil\", price: 9000) { id name } }" }

POST /graphql
{ "query": "subscription { productAdded { name price } }" }
```

The subscription is `text/event-stream` on the same path (`subscriptions = Sse`, the default).
`subscriptions = GraphqlWs` serves `graphql-ws` on that path instead.

The GraphQL schema is the files under `resources/graphql/` (`schema.graphqls`, `product.graphqls`,
`review.graphqls`, `search.graphqls`), merged the way Spring GraphQL merges a split document.
`Catalog` is the DataFetchers for those fields.

`search.graphqls` declares `union SearchResult = Product | Review` and nothing registers a type
resolver for it: Graphix matches each row on its Kotlin class name. The resolver returns
`List<Any>`, because Kotlin has no union type and, on this path, the document is the schema — a
resolver's Kotlin return type is never read.

In-memory, no database. The point is the plugin: `install(GraphQL) { schema { query(catalog); mutation(catalog); subscription(catalog); type(catalog) } }`.
`Catalog` is the store, the roots and the type fields: `reviews` is a `@BatchMapping` on Product, one
load for the list, not one per product. A Spring app would put `OrderService` on the controller
constructor instead; that is still not GraphQL context.
[`docs/graphix.md`](../../docs/graphix.md) draws the line.
