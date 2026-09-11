# demo-spring-client

The Spring side of the generator: the same [`demo-api` document](../demo-api/openapi.yaml) generated
as `@HttpExchange` interfaces over `WebClient`, bound by Jackson 3.

```bash
./kotlin test -m demo-spring-client
```

## Two documents, one module

This is the case the plugin's `specs` list exists for. A client of more than one upstream API used
to need a module per API, because the plugin took a single `specFile` and a single `packageName`.
Here it generates both `demo-api`'s document and the bundle `redocly` writes for
[`spring-orders`](../spring-orders/README.md), into two packages:

```yaml
specs:
  - spec: ../demo-api/openapi.yaml
    packageName: com.softistx.demo.spring.api
    client: Spring
  - spec: ../spring-orders/openapi/api-docs.yaml
    packageName: com.softistx.demo.spring.orders
    client: Spring
```

Nothing calls the second one. It is generated to prove that a second document lands beside the first
with its own models, its own interfaces and its own `Endpoints`, and that the two do not tread on
each other — every spec writes into one output directory, so the packages must differ, and the
plugin refuses the configuration where they do not.

## Why it repeats `demo-client`'s tests

`AuthTest`, `ErrorTest`, `PolymorphismTest` and `EndToEndTest` ask the same questions as their
[kotlinx counterparts](../demo-client/README.md) because the answers are allowed to differ and must
not. One document, two serialization libraries, two HTTP clients: where the two disagree about what
a schema means, at least one of them is reading it wrong, and that is a defect in the generator
rather than in either client.

`GeneratedClientTest` adds what is specific to this side — that the interface Spring proxies carries
the annotations `HttpServiceProxyFactory` needs, and that each operation binds to the method, path
and body the document names.

---

Apache-2.0 · [Contributing](../../CONTRIBUTING.md) · [All the libraries](../../README.md)
