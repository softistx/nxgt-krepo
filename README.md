# nxgt-krepo

A multi-module Kotlin repository built with the **JetBrains Kotlin Toolchain** (the `kotlin` CLI,
formerly Amper) — no Gradle, no Maven, no `gradlew`. A module is a directory with a `module.yaml`,
registered by path in `project.yaml`.

Its first subject is an **OpenAPI-to-Kotlin client generator**, built as four modules that form one
chain:

```
examples/demo-api/openapi.yaml     one document
        │
libs/openapi-generator         reads it, emits models and a typed client (KotlinPoet)
        │
plugins/openapi                wraps the generator as a toolchain build task
        │
examples/demo-client               a Ktorfit client, kotlinx.serialization  ─┐
examples/demo-spring-client        a Spring @HttpExchange client, Jackson 3 ─┴─ both call examples/demo-api
```

Two generated clients drive one hand-written server over real HTTP, so a disagreement between the
two serialization libraries about what the document means fails a test rather than shipping.

`examples/jpa-shop` is separate from that chain: a Ktor catalogue over Postgres showing
`shared-jpa`'s repository, service and audit layer end to end.

Alongside them are the shared service libraries, which have nothing to do with the generator:

| | |
| --- | --- |
| `libs/shared-common` | The coroutine primitives and the one lenient `Json` the libraries below share: a mutex-guarded map, a lock per key, and the mailbox that carries a Java callback's work into a coroutine |
| `libs/shared-amqp` | An AMQP connection over the RabbitMQ client: topology declared in one block, publishes that wait for the broker's confirm, deliveries as a `Flow`, and retries that are delay queues rather than a loop |
| `libs/shared-i18n` | Catalogs read strictly as UTF-8 and compiled at startup, a message resolved key by key down the locale chain, and `Accept-Language` negotiated against what is actually shipped |
| `libs/shared-jpa` | Postgres over Hibernate Reactive: ordinary annotated Kotlin entities, every session pinned to the event loop that opened it so a handler can suspend mid-transaction, and HQL, SQL and a typed `KProperty` query DSL over Criteria through one suspending builder |
| `libs/shared-kafka` | A cluster and the clients over it: sends that suspend until the broker acknowledges them, records as a `Flow` with the offsets looked after, and topics and group lag from an admin client |
| `libs/shared-ktor` | Ktor integrations for the libraries here: `install(RedisConnection)`, then `call.redis` in a handler — one connection per application, opened with it and closed with it — and the same for Mongo, AMQP, Kafka, Postgres, object storage and i18n |
| `libs/shared-koin` | The same seven backends as Koin modules, for a worker or a CLI with no web framework: `redisModule(config)`, and the container closes what it built |
| `libs/shared-mongo` | Session-aware collection extensions, keyset pagination, a CRUD repository and the write flow over it, and a coroutine GridFS bucket |
| `libs/shared-redis` | A namespaced connection over Lettuce owning one `Json`, and the four kotlinx-serialized things built on one: a typed cache, a lock, topics, and streams with consumer groups |
| `libs/shared-storage` | S3-compatible object storage over the MinIO SDK: buckets and objects as coroutines, and presigned URLs and upload forms for browsers |
| `libs/shared-testing` | What the integration specs run against: a backing service reused from the environment when one is named, and started as a container for the run when it is not |

## Getting started

```bash
./kotlin build          # compile everything
./kotlin test           # run every module's tests
./kotlin show modules   # module names accepted by -m
```

Use `./kotlin`, not a bare `kotlin`: the wrapper pins the toolchain version.

## Where to read next

| | |
| --- | --- |
| [`docs/openapi-support.md`](docs/openapi-support.md) | What the generator understands: type mapping, composition, enums, vendor extensions, and what it does not handle |
| [`libs/openapi-generator/README.md`](libs/openapi-generator/README.md) | The generator itself — its shape, what each client emitter produces, how to add one |
| [`libs/shared-common/README.md`](libs/shared-common/README.md) | The shared module — what belongs in it, and which concurrency type a given caller wants |
| [`libs/shared-amqp/README.md`](libs/shared-amqp/README.md) | The AMQP library — exchanges and queues, what a confirm promises, and what prefetch is for |
| [`libs/shared-i18n/README.md`](libs/shared-i18n/README.md) | The i18n library — catalogs, the per-key locale walk, the missing-key policy, and negotiation |
| [`libs/shared-ktor/README.md`](libs/shared-ktor/README.md) | The Ktor integrations — the seven plugins, what each owns, and how one module holds them without a fat dependency list |
| [`libs/shared-koin/README.md`](libs/shared-koin/README.md) | The Koin modules — why the container creates the connection here and adopts it there, and what has no `onClose` |
| [`libs/shared-jpa/README.md`](libs/shared-jpa/README.md) | The Postgres library — the session confinement rule everything else follows from, entities, queries, and what is deliberately not here |
| [`libs/shared-kafka/README.md`](libs/shared-kafka/README.md) | The Kafka library — publishing, the poll loop and its commits, and what at-least-once costs |
| [`libs/shared-mongo/README.md`](libs/shared-mongo/README.md) | The MongoDB library — its packages, and the reasoning behind the parts that are not obvious |
| [`libs/shared-redis/README.md`](libs/shared-redis/README.md) | The Redis library — the cache, the lock, topics and streams, and what each one refuses to do |
| [`libs/shared-storage/README.md`](libs/shared-storage/README.md) | The object storage library — objects, and what a presigned URL or upload form can promise |
| [`libs/shared-testing/README.md`](libs/shared-testing/README.md) | The test support — where a spec's server comes from, and what cleans a container up afterwards |
| [`plugins/openapi/README.md`](plugins/openapi/README.md) | The build plugin: settings, and what each choice needs on the consuming module's classpath |
| [`AGENTS.md`](AGENTS.md) | Build commands, module layout, and the conventions this repo holds itself to |
