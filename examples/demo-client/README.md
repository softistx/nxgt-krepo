# demo-client

The kotlinx side of the generator: a Ktorfit client generated from
[`demo-api`'s document](../demo-api/openapi.yaml), with kotlinx.serialization and
`kotlinx.datetime`.

```bash
./kotlin test -m demo-client
```

## What it is for

Nothing here is written by hand except `src/DemoClient.kt`, which calls a few operations so the
generated code has a caller that compiles. The module's real content is `test/`, and the tests run
against the **real `demo-api` server** — `//examples/demo-api` is a test dependency, and each spec
starts one on a free port in `beforeSpec` — rather than against a mock. A generated client that
satisfies a mock has proved that the mock and the generator agree, which is not the question.

Four things get asked, each in its own spec:

| spec | what would otherwise go unnoticed |
| --- | --- |
| `AuthTest` | the root security scheme is attached, `security: []` overrides it, and the token slot is read per request rather than captured when the client was built |
| `ErrorTest` | a documented status arrives as its own exception with the body parsed; an undocumented one still reaches the caller; a body that does not parse does not throw a second exception over the first |
| `PolymorphismTest` | a `oneOf` resolves to the right subtype in both directions — with a `discriminator`, and without one, where the shapes are told apart only by the keys they carry |
| `EndToEndTest` | the ordinary read/write path, including that `PATCH` leaves omitted fields alone |

## Why there are two demo clients

[`demo-spring-client`](../demo-spring-client/README.md) generates from the same document with
Jackson and `@HttpExchange`. Two serialization libraries reading one document is the arrangement
that catches an ambiguity in how the generator maps a schema: when the two disagree, at least one of
them is wrong, and the failure says so in a test rather than in a consumer's project.

---

Apache-2.0 · [Contributing](../../CONTRIBUTING.md) · [All the libraries](../../README.md)
