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
| `libs/stx-common` | What more than one library here needs and nothing else, in two concurrency packages split by whether the caller can suspend — `CoroutineSafeMap`, `KeyedMutex` and `Mailbox` for the ones that can, `Memo`, `Guarded` and the concurrent-collection extensions for the Hibernate binders and SLF4J initialisers that cannot — plus `CloseGuard`, the keyset pagination both stores share, and the one lenient `Json` |
| `libs/stx-amqp` | An AMQP connection over the RabbitMQ client: topology declared in one block, publishes that wait for the broker's confirm, deliveries as a `Flow`, and retries that are delay queues rather than a loop |
| `libs/stx-i18n` | Catalogs read strictly as UTF-8 and compiled at startup, a message resolved key by key down the locale chain, and `Accept-Language` negotiated against what is actually shipped |
| `libs/stx-jpa` | Postgres over Hibernate Reactive: ordinary annotated Kotlin entities, every session pinned to the event loop that opened it so a handler can suspend mid-transaction, and HQL, SQL and JPA Criteria — named by `KProperty` rather than by strings — through one suspending builder |
| `libs/stx-material` | The one client-side library: Compose Multiplatform components over Material 3 — a whole palette derived from one colour seed, a component's look declared as a `Style` whose pressed and hovered states animate themselves, and motion as named durations instead of scattered `tween`s |
| `libs/stx-kafka` | A cluster and the clients over it: sends that suspend until the broker acknowledges them, records as a `Flow` with the offsets looked after, and topics and group lag from an admin client |
| `libs/stx-ktor` | The Ktor foundation every plugin here is built on: `own`/`publish`/`resource`/`required` — a connection opened with the application and closed with it, and a missing `install` that names itself — plus one CORS policy shared with Spring. The plugins themselves are modules beside their libraries: `stx-redis-ktor`, `stx-mongo-ktor`, `stx-jpa-ktor`, `stx-amqp-ktor`, `stx-kafka-ktor`, `stx-storage-ktor`, `stx-i18n-ktor` |
| `libs/stx-mongo` | Session-aware collection extensions covering CRUD, keyset pagination, an opt-in audit trail, and a coroutine GridFS bucket |
| `libs/stx-redis` | A namespaced connection over Lettuce owning one `Json`, and the four kotlinx-serialized things built on one: a typed cache, a lock, topics, and streams with consumer groups |
| `libs/stx-spring-boot` | Spring Boot integration for the libraries here: one auto-configuration per library, every bean off unless a `stx.*` property asks for it, plus the WebFlux helpers — a translated error body and the request's own locale, taken from the exchange rather than a `ThreadLocal` |
| `libs/stx-storage` | S3-compatible object storage over the MinIO SDK: buckets and objects as coroutines, and presigned URLs and upload forms for browsers |
| `libs/stx-graphix` | GraphQL over graphql-java 25: annotated Kotlin functions, `@Serializable` types, suspending execution. `stx-graphix-ktor` and `stx-graphix-spring` are the HTTP integrations |
| `libs/stx-workflow` | Workflows that survive a restart: steps declared in order, each with the compensation that undoes it, one typed context threaded through them, and the state written down after every node — so a crash resumes rather than restarts. A workflow can also stop on purpose, for a person's approval, for a clock, or for another workflow it delegated to, and outlive the process it stopped in. Declared as a DSL or as annotations on a class — the two produce the same object — and wired into Ktor or Spring Boot by `stx-workflow-ktor` and `stx-workflow-spring`. `stx-workflow-db` keeps them in Redis, in SQL through `stx-jpa`, or in MongoDB |
| `libs/stx-migrations` | Schema migrations that are Kotlin all the way down, on neither Flyway nor Liquibase: a migration is a class with a version it declares, the ledger records what ran and who ran it, and a renewing lease keeps two instances of the application from running the same one twice. It is a **gate** — it runs in the process about to serve, before it serves, and a failure is an application that does not start. `stx-migrations-db` holds the ledger for MongoDB and for SQL through `stx-jpa`; `stx-migrations-ktor` and `stx-migrations-spring` are the two integrations |
| `libs/stx-telemetry` | Logs and traces for a service made of coroutines: the current span is a `CoroutineContext` element rather than a `ThreadLocal`, so it is still right after a suspension moves the work — which is exactly what an MDC gets wrong. An event is a `@Serializable` type, whose serial name names it and whose fields are its attributes, so choosing what is logged is the same act as declaring a type. Signals queue without ever making a caller wait and drain to the exporters in one coroutine |
| `libs/stx-telemetry-otlp` | Ships those logs and traces to an OTLP collector over HTTP in JSON, with kotlinx.serialization and the JDK's own `HttpClient` — no OpenTelemetry SDK, and no dependency but the core module |
| `libs/stx-telemetry-slf4j` | The SLF4J bridge, both ways: an SPI provider that puts every third-party library's logs into the pipeline carrying the current span, or an exporter that writes this library's signals out to a logback an application already has |
| `libs/stx-telemetry-mongo` | Those same logs and traces into a MongoDB collection, with the field names the JSON-lines format uses and retention as a TTL index — so the deleting is Mongo's background task rather than a job somebody has to own. It opens a pool of its own, because a burst of telemetry must not exhaust the one business requests are queueing for |
| `libs/stx-telemetry-ktor` | The Ktor plugin: one telemetry per application and a server span per request, continuing an incoming `traceparent` and renamed to the matched route once Ktor knows it |
| `libs/stx-telemetry-spring` | The Spring Boot auto-configuration: one telemetry behind `stx.telemetry.enabled`, every `Exporter` bean added to it, and a `CoWebFilter` — not a `WebFilter` — so a suspending `@RestController` method is inside the request's span |
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
| [`libs/stx-common/README.md`](libs/stx-common/README.md) | The shared module — what belongs in it, which concurrency type a given caller wants, why `getOrPut` on a `ConcurrentHashMap` is not atomic, and what the standard library already covers |
| [`libs/stx-amqp/stx-amqp/README.md`](libs/stx-amqp/stx-amqp/README.md) | The AMQP library — exchanges and queues, what a confirm promises, and what prefetch is for |
| [`libs/stx-amqp/stx-amqp-ktor/README.md`](libs/stx-amqp/stx-amqp-ktor/README.md) | The Ktor plugin — why it owns the connection and not the channel, and why the connect blocks |
| [`libs/stx-i18n/stx-i18n/README.md`](libs/stx-i18n/stx-i18n/README.md) | The i18n library — catalogs, the per-key locale walk, the missing-key policy, and negotiation |
| [`libs/stx-i18n/stx-i18n-ktor/README.md`](libs/stx-i18n/stx-i18n-ktor/README.md) | The Ktor plugin — why the locale is resolved once at call setup, and why `?lang=` is off by default |
| [`libs/stx-ktor/README.md`](libs/stx-ktor/README.md) | The Ktor foundation — the four lifecycle verbs and the rule behind them, what Ktor's container does to what a plugin registers, and why no integration lives here |
| [`libs/stx-spring-boot/README.md`](libs/stx-spring-boot/README.md) | The Spring integration — the opt-in `stx.*` model, why the IDE metadata is written by hand, and what `compile-only` buys a consumer |
| [`docs/spring-mongo-queries.md`](docs/spring-mongo-queries.md) | What a stx-spring-boot Mongo query may say — the operators, the filter and sort grammars, and the keyset paging rules |
| [`docs/spring-configuration.md`](docs/spring-configuration.md) | Every `stx.*` key an application may set, its default, and what switching it on costs |
| [`libs/stx-jpa/stx-jpa/README.md`](libs/stx-jpa/stx-jpa/README.md) | The Postgres library — the session confinement rule everything else follows from, and why each part is shaped the way it is |
| [`libs/stx-jpa/stx-jpa-ktor/README.md`](libs/stx-jpa/stx-jpa-ktor/README.md) | The Ktor plugin — why it owns a factory and not a session, why `runBlocking` inside `install`, and why a scan that finds nothing fails |
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
| [`libs/stx-material/README.md`](libs/stx-material/README.md) | The UI library — its shape, how `StxTheme` slots into an existing Material 3 application, and how a component is added |
| [`libs/stx-material/docs/tokens.md`](libs/stx-material/docs/tokens.md) | What a token may say — colour roles, spacing, durations and easings, and why shapes and elevation stay M3's |
| [`libs/stx-material/docs/components.md`](libs/stx-material/docs/components.md) | Every component, its parameters, and its story in the catalogue |
| [`examples/workflow-checkout/README.md`](examples/workflow-checkout/README.md) | The checkout saga — compensation, a fan-out, and a process killed mid-charge to show what at-least-once buys and costs |
| [`examples/material-demo/README.md`](examples/material-demo/README.md) | The catalogue — why it is three modules, how to run it, how a story is registered |
| [`libs/stx-kafka/stx-kafka/README.md`](libs/stx-kafka/stx-kafka/README.md) | The Kafka library — publishing, the poll loop and its commits, and what at-least-once costs |
| [`libs/stx-kafka/stx-kafka-ktor/README.md`](libs/stx-kafka/stx-kafka-ktor/README.md) | The Ktor plugin — the one here that opens nothing, and why a Kafka client makes that the right shape |
| [`libs/stx-mongo/stx-mongo/README.md`](libs/stx-mongo/stx-mongo/README.md) | The MongoDB library — its packages, and the reasoning behind the parts that are not obvious |
| [`libs/stx-mongo/stx-mongo-ktor/README.md`](libs/stx-mongo/stx-mongo-ktor/README.md) | The Ktor plugin — why the client is built in the library and not in the plugin, and why `uri` and `database` have no defaults |
| [`docs/telemetry.md`](docs/telemetry.md) | What a stx-telemetry call may say — the root's settings, the log and span verbs, the severities, the attribute rules, `traceparent` and the signal model |
| [`libs/stx-telemetry/stx-telemetry/README.md`](libs/stx-telemetry/stx-telemetry/README.md) | The telemetry library — why the span is a coroutine context element and not an MDC, why an event is a type, and why writing a log never waits |
| [`libs/stx-telemetry/stx-telemetry-otlp/README.md`](libs/stx-telemetry/stx-telemetry-otlp/README.md) | The OTLP exporter — why not the Java SDK, the two encoding details that are easy to get wrong, and what is retried |
| [`libs/stx-telemetry/stx-telemetry-slf4j/README.md`](libs/stx-telemetry/stx-telemetry-slf4j/README.md) | The SLF4J bridge — which direction to pick, why reading a third-party MDC is not a contradiction, and why both directions at once is refused |
| [`libs/stx-telemetry/stx-telemetry-mongo/README.md`](libs/stx-telemetry/stx-telemetry-mongo/README.md) | The MongoDB exporter — why the document is the line format plus the resource, why the instants are written back as dates, and why retention is an index and not a job |
| [`libs/stx-telemetry/stx-telemetry-ktor/README.md`](libs/stx-telemetry/stx-telemetry-ktor/README.md) | The Ktor plugin — why the span wraps the pipeline instead of being two hooks, why it is renamed after routing, and what a thrown handler costs the span |
| [`libs/stx-telemetry/stx-telemetry-spring/README.md`](libs/stx-telemetry/stx-telemetry-spring/README.md) | The Spring auto-configuration — why `CoWebFilter` and not `WebFilter`, the dependency the specs found by hanging, and the 200 nobody set |
| [`docs/workflow.md`](docs/workflow.md) | What a stx-workflow declaration may say — the verbs, the step scope, the statuses, the record and the store contract |
| [`libs/stx-workflow/stx-workflow/README.md`](libs/stx-workflow/stx-workflow/README.md) | The workflow engine — checkpointing rather than replay, what at-least-once asks of a step, why a fan-out merges explicitly, and why a child is an instance rather than a call |
| [`libs/stx-workflow/stx-workflow-db/README.md`](libs/stx-workflow/stx-workflow-db/README.md) | Where instances live — why one module and not three, and what each store does with an index, a lease and retention |
| [`libs/stx-workflow/stx-workflow-spring/README.md`](libs/stx-workflow/stx-workflow-spring/README.md) | The Spring auto-configuration — why an integration is a module beside its library, and why nothing is inferred about where instances live |
| [`libs/stx-workflow/stx-workflow-ktor/README.md`](libs/stx-workflow/stx-workflow-ktor/README.md) | The Ktor plugin — and why `own`/`publish`/`required` had to become public |
| [`docs/migrations.md`](docs/migrations.md) | What a stx-migrations migration may say — the version, the statuses, the runner's order, the ledger contract, the lock, and what a killed process leaves |
| [`libs/stx-migrations/stx-migrations/README.md`](libs/stx-migrations/stx-migrations/README.md) | The migration library — why the version is declared rather than parsed, why a gate throws where the prior art logged, and why neither Flyway nor Liquibase is underneath it |
| [`libs/stx-migrations/stx-migrations-db/README.md`](libs/stx-migrations/stx-migrations-db/README.md) | Where the ledger lives — why one module and not two, and per store: the uniqueness, the lock, the instants, and how DDL reaches a database with no JDBC in front of it |
| [`libs/stx-migrations/stx-migrations-ktor/README.md`](libs/stx-migrations/stx-migrations-ktor/README.md) | The Ktor plugin — why `runBlocking` inside `install` is what makes it a gate, why it goes after the connection plugin, and why `sql { }` / `mongo { }` are sugar over the one `gate` that is the contract |
| [`libs/stx-migrations/stx-migrations-spring/README.md`](libs/stx-migrations/stx-migrations-spring/README.md) | The Spring auto-configuration — why an `InitializingBean` and not a suspending listener, and why naming a store without its connection is an error rather than a shrug |
| [`libs/stx-redis/stx-redis/README.md`](libs/stx-redis/stx-redis/README.md) | The Redis library — the cache, the lock, topics and streams, and what each one refuses to do |
| [`libs/stx-redis/stx-redis-ktor/README.md`](libs/stx-redis/stx-redis-ktor/README.md) | The Ktor plugin — why one connection and not one per request, and what `injectable = true` costs and does not |
| [`libs/stx-storage/stx-storage/README.md`](libs/stx-storage/stx-storage/README.md) | The object storage library — objects, and what a presigned URL or upload form can promise |
| [`libs/stx-storage/stx-storage-ktor/README.md`](libs/stx-storage/stx-storage-ktor/README.md) | The Ktor plugin — and why it is the one whose `config` is required rather than defaulted |
| [`libs/stx-testing/README.md`](libs/stx-testing/README.md) | The test support — where a spec's server comes from, and what cleans a container up afterwards |
| [`plugins/openapi/README.md`](plugins/openapi/README.md) | The build plugin: settings, and what each choice needs on the consuming module's classpath |
| [`AGENTS.md`](AGENTS.md) | Build commands, module layout, and the conventions this repo holds itself to |
