# demo-api

The server the generator is aimed at: one hand-written Ktor API, and the OpenAPI document that
describes it.

```bash
./kotlin run -m demo-api        # http://localhost:8080
```

## Why a hand-written server

`openapi.yaml` is the input to [`libs/api/stx-openapi-generator`](../../libs/api/stx-openapi-generator/README.md),
and `src/` is a server written by hand to match it. Nothing generates the server from the document —
that is deliberate, and it is what makes the pair worth having. If both sides came from the same
generator, a misreading of the document would be symmetric and no test could see it. Here the
document is read twice by two different code paths, and the two [demo](../demo-client/README.md)
[clients](../demo-spring-client/README.md) call this server over real HTTP, so a disagreement fails
a test instead of shipping.

The 32 paths are chosen for what they make the generator do, not for what an application would
need. `/auth/*` is the root security scheme and the operations that override it with `security: []`;
`/categories`, `/tags`, `/permissions`, `/roles` and `/users` are the ordinary CRUD shape with
`PATCH` semantics that have to leave omitted fields alone; `/notifications` carries both kinds of
`oneOf` — `NotificationPayload` with a `discriminator`, and `Recipient` without one, told apart only
by the keys each shape carries; `/uploads` is `multipart/form-data`; `/session/echo` reports the
`Authorization` header the server actually received, which is the only way to observe that a client
attached nothing where the document said to; and `/failures/{mode}` returns, on demand, each of the
failures a client has to handle — a documented status with a body, a documented status without one,
an undocumented status, and a body that does not parse.

## The store is a map

`src/store/Store.kt` is in-memory and `src/routes/DemoData.kt` seeds it at startup. There is no
database because none of what this module exists to prove involves one, and a container per test run
would be a cost paid for nothing. A restart is a reset; that is a feature here.

---

Apache-2.0 · [Contributing](../../CONTRIBUTING.md) · [All the libraries](../../README.md)
