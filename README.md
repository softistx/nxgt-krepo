# nxgt-krepo

A multi-module Kotlin repository built with the **JetBrains Kotlin Toolchain** (the `kotlin` CLI,
formerly Amper) — no Gradle, no Maven, no `gradlew`. A module is a directory with a `module.yaml`,
registered by path in `project.yaml`.

Its first subject is an **OpenAPI-to-Kotlin client generator**, built as four modules that form one
chain:

```
examples/demo-api/openapi.yaml     one document
        │
libs/stx-openapi-generator         reads it, emits models and a typed client (KotlinPoet)
        │
plugins/openapi                wraps the generator as a toolchain build task
        │
examples/demo-client               a Ktorfit client, kotlinx.serialization  ─┐
examples/demo-spring-client        a Spring @HttpExchange client, Jackson 3 ─┴─ both call examples/demo-api
```

Two generated clients drive one hand-written server over real HTTP, so a disagreement between the
two serialization libraries about what the document means fails a test rather than shipping.

`examples/jpa-shop` is separate from that chain: a Ktor catalogue over Postgres showing
`stx-jpa`'s CRUD extensions, transaction guard and audit layer end to end. So is
`examples/graphix-shop`, a Ktor GraphQL catalogue over `stx-graphix-ktor`,
`examples/graphix-codegen`, the two GraphQL codegen plugins on one schema, and
`examples/material-demo`, the catalogue for the UI library — `./kotlin run -m md-desktop`.

Alongside them are the shared service libraries, which have nothing to do with the generator:

| | |
| --- | --- |
| `libs/stx-common` | The coroutine primitives and the one lenient `Json` the libraries below share: a mutex-guarded map, a lock per key, and the mailbox that carries a Java callback's work into a coroutine |
| `libs/stx-amqp` | An AMQP connection over the RabbitMQ client: topology declared in one block, publishes that wait for the broker's confirm, deliveries as a `Flow`, and retries that are delay queues rather than a loop |
| `libs/stx-i18n` | Catalogs read strictly as UTF-8 and compiled at startup, a message resolved key by key down the locale chain, and `Accept-Language` negotiated against what is actually shipped |
| `libs/stx-jpa` | Postgres over Hibernate Reactive: ordinary annotated Kotlin entities, every session pinned to the event loop that opened it so a handler can suspend mid-transaction, and HQL, SQL and JPA Criteria — named by `KProperty` rather than by strings — through one suspending builder |
| `libs/stx-material` | The one client-side library: Compose Multiplatform components over Material 3 — a whole palette derived from one colour seed, a component's look declared as a `Style` whose pressed and hovered states animate themselves, and motion as named durations instead of scattered `tween`s |
| `libs/stx-kafka` | A cluster and the clients over it: sends that suspend until the broker acknowledges them, records as a `Flow` with the offsets looked after, and topics and group lag from an admin client |
| `libs/stx-ktor` | Ktor integrations for the libraries here: `install(RedisConnection)`, then `call.redis` in a handler — one connection per application, opened with it and closed with it — and the same for Mongo, AMQP, Kafka, Postgres, object storage and i18n |
| `libs/stx-koin` | The same seven backends as Koin modules, for a worker or a CLI with no web framework: `redisModule(config)`, and the container closes what it built |
| `libs/stx-mongo` | Session-aware collection extensions covering CRUD, keyset pagination, an opt-in audit trail, and a coroutine GridFS bucket |
| `libs/stx-redis` | A namespaced connection over Lettuce owning one `Json`, and the four kotlinx-serialized things built on one: a typed cache, a lock, topics, and streams with consumer groups |
| `libs/stx-spring-boot` | Spring Boot integration for the libraries here: one auto-configuration per library, every bean off unless a `stx.*` property asks for it, plus the WebFlux helpers — a translated error body and the request's own locale, taken from the exchange rather than a `ThreadLocal` |
| `libs/stx-storage` | S3-compatible object storage over the MinIO SDK: buckets and objects as coroutines, and presigned URLs and upload forms for browsers |
| `libs/stx-graphix` | GraphQL over graphql-java 25: annotated Kotlin functions, `@Serializable` types, suspending execution. `stx-graphix-ktor` and `stx-graphix-spring` are the HTTP integrations |
| `libs/stx-workflow` | Workflows that survive a restart: steps declared in order, each with the compensation that undoes it, one typed context threaded through them, and the state written down after every node — so a crash resumes rather than restarts. A workflow can also stop on purpose, for a person's approval or for a clock, and outlive the process it stopped in. Declared as a DSL or as annotations on a class — the two produce the same object — and wired into Ktor or Spring Boot by `stx-workflow-ktor` and `stx-workflow-spring`. `stx-workflow-db` keeps them in Redis, in SQL through `stx-jpa`, or in MongoDB |
| `libs/stx-testing` | What the integration specs run against: a backing service reused from the environment when one is named, and started as a container for the run when it is not |

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
| [`libs/stx-openapi-generator/README.md`](libs/stx-openapi-generator/README.md) | The generator itself — its shape, what each client emitter produces, how to add one |
| [`libs/stx-common/README.md`](libs/stx-common/README.md) | The shared module — what belongs in it, and which concurrency type a given caller wants |
| [`libs/stx-amqp/README.md`](libs/stx-amqp/README.md) | The AMQP library — exchanges and queues, what a confirm promises, and what prefetch is for |
| [`libs/stx-i18n/README.md`](libs/stx-i18n/README.md) | The i18n library — catalogs, the per-key locale walk, the missing-key policy, and negotiation |
| [`libs/stx-ktor/README.md`](libs/stx-ktor/README.md) | The Ktor integrations — the seven plugins, what each owns, and how one module holds them without a fat dependency list |
| [`libs/stx-koin/README.md`](libs/stx-koin/README.md) | The Koin modules — why the container creates the connection here and adopts it there, and what has no `onClose` |
| [`libs/stx-spring-boot/README.md`](libs/stx-spring-boot/README.md) | The Spring integration — the opt-in `stx.*` model, why the IDE metadata is written by hand, and what `compile-only` buys a consumer |
| [`docs/spring-mongo-queries.md`](docs/spring-mongo-queries.md) | What a stx-spring-boot Mongo query may say — the operators, the filter and sort grammars, and the keyset paging rules |
| [`docs/spring-configuration.md`](docs/spring-configuration.md) | Every `stx.*` key an application may set, its default, and what switching it on costs |
| [`libs/stx-jpa/README.md`](libs/stx-jpa/README.md) | The Postgres library — the session confinement rule everything else follows from, and why each part is shaped the way it is |
| [`docs/jpa-criteria.md`](docs/jpa-criteria.md) | What a stx-jpa query may say — operators, joins, fetch joins, entity graphs, projections, and the two escapes |
| [`docs/jpa-mapping.md`](docs/jpa-mapping.md) | What a stx-jpa entity may say — the database, column names, identifiers, `Instant`/`Uuid`, JSON columns, validation |
| [`docs/graphix.md`](docs/graphix.md) | What a stx-graphix schema may say — the annotations, scalars, field directives, DataLoaders, SDL scan, HTTP/SSE/graphql-ws |
| [`libs/stx-graphix/stx-graphix/README.md`](libs/stx-graphix/stx-graphix/README.md) | The GraphQL engine — why SerialDescriptor and not Jackson, why there is no class scan in core |
| [`libs/stx-graphix/stx-graphix-ktor/README.md`](libs/stx-graphix/stx-graphix-ktor/README.md) | The Ktor plugin — path, `instance` vs `schema { }`, `fromDi`, `injectable` |
| [`libs/stx-graphix/stx-graphix-spring/README.md`](libs/stx-graphix/stx-graphix-spring/README.md) | The Spring Boot plugin — `stx.graphix.enabled`, `@GraphQLController` scan |
| [`examples/graphix-shop/README.md`](examples/graphix-shop/README.md) | The GraphQL catalogue — how to run it, the split SDL under `resources/graphql/` |
| [`plugins/dgs-codegen/README.md`](plugins/dgs-codegen/README.md) | DGS codegen plugin — schema to Kotlin types |
| [`plugins/apollo/README.md`](plugins/apollo/README.md) | Apollo codegen plugin — schema and documents to Kotlin models |
| [`examples/graphix-codegen/README.md`](examples/graphix-codegen/README.md) | Both plugins on one schema |
| [`libs/stx-material/README.md`](libs/stx-material/README.md) | The UI library — its shape, how `StrangeTheme` slots into an existing Material 3 application, and how a component is added |
| [`libs/stx-material/docs/tokens.md`](libs/stx-material/docs/tokens.md) | What a token may say — colour roles, spacing, durations and easings, and why shapes and elevation stay M3's |
| [`libs/stx-material/docs/components.md`](libs/stx-material/docs/components.md) | Every component, its parameters, and its story in the catalogue |
| [`examples/workflow-checkout/README.md`](examples/workflow-checkout/README.md) | The checkout saga — compensation, a fan-out, and a process killed mid-charge to show what at-least-once buys and costs |
| [`examples/material-demo/README.md`](examples/material-demo/README.md) | The catalogue — why it is three modules, how to run it, how a story is registered |
| [`libs/stx-kafka/README.md`](libs/stx-kafka/README.md) | The Kafka library — publishing, the poll loop and its commits, and what at-least-once costs |
| [`libs/stx-mongo/README.md`](libs/stx-mongo/README.md) | The MongoDB library — its packages, and the reasoning behind the parts that are not obvious |
| [`docs/workflow.md`](docs/workflow.md) | What a stx-workflow declaration may say — the verbs, the step scope, the statuses, the record and the store contract |
| [`libs/stx-workflow/stx-workflow/README.md`](libs/stx-workflow/stx-workflow/README.md) | The workflow engine — checkpointing rather than replay, what at-least-once asks of a step, and why a fan-out merges explicitly |
| [`libs/stx-workflow/stx-workflow-db/README.md`](libs/stx-workflow/stx-workflow-db/README.md) | Where instances live — why one module and not three, and what each store does with an index, a lease and retention |
| [`libs/stx-workflow/stx-workflow-spring/README.md`](libs/stx-workflow/stx-workflow-spring/README.md) | The Spring auto-configuration — why an integration is a module beside its library, and why nothing is inferred about where instances live |
| [`libs/stx-workflow/stx-workflow-ktor/README.md`](libs/stx-workflow/stx-workflow-ktor/README.md) | The Ktor plugin — and why `own`/`publish`/`required` had to become public |
| [`libs/stx-redis/README.md`](libs/stx-redis/README.md) | The Redis library — the cache, the lock, topics and streams, and what each one refuses to do |
| [`libs/stx-storage/README.md`](libs/stx-storage/README.md) | The object storage library — objects, and what a presigned URL or upload form can promise |
| [`libs/stx-testing/README.md`](libs/stx-testing/README.md) | The test support — where a spec's server comes from, and what cleans a container up afterwards |
| [`plugins/openapi/README.md`](plugins/openapi/README.md) | The build plugin: settings, and what each choice needs on the consuming module's classpath |
| [`AGENTS.md`](AGENTS.md) | Build commands, module layout, and the conventions this repo holds itself to |
