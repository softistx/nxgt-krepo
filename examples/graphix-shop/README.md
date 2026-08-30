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
```

In-memory, no database. The point is the plugin: `install(GraphQL) { schema { query(catalog); mutation(catalog) } }`.
