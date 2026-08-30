# graphql-shop

A shop catalogue over GraphQL — the smallest thing that shows `stx-graphql-ktor` end to end.

```
./kotlin run -m graphql-shop
```

```
POST /graphql
{ "query": "{ products { name price } }" }

POST /graphql
{ "query": "mutation { addProduct(name: \"Anvil\", price: 9000) { id name } }" }
```

In-memory, no database. The point is the plugin: `install(GraphQL) { schema { query(catalog); mutation(catalog) } }`.
