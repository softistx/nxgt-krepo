# graphix-codegen

The smallest module that turns on both GraphQL codegen plugins against one schema.

```
./kotlin test -m graphix-codegen
```

`resources/graphql/schema.graphqls` is the schema. `dgs-codegen` emits Kotlin types
(`AddProductInput`). `apollo` reads `resources/graphql/documents/Products.graphql` and emits
`ProductsQuery`, including `OPERATION_DOCUMENT` — the string a Graphix HTTP test POSTs.

The plugins are adapters of Netflix DGS codegen and Apollo Kotlin `apollo-compiler`. How to
enable them is in [`plugins/dgs-codegen/README.md`](../../plugins/dgs-codegen/README.md) and
[`plugins/apollo/README.md`](../../plugins/apollo/README.md).

---

Apache-2.0 · [Contributing](../../CONTRIBUTING.md) · [All the libraries](../../README.md)
