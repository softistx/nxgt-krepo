# AGENTS.md

Shared guidance for any coding agent working in this repository.

## What this repo is

`nxgt-krepo` is a multi-module Kotlin library repository built with the **JetBrains Kotlin Toolchain 0.12+** (the `kotlin` CLI, formerly Amper) — *not* Gradle and *not* Maven. There is no `build.gradle.kts`, no `settings.gradle.kts`, and no `gradlew`.

What exists:

| Path | Role |
| --- | --- |
| `project.yaml` | Project manifest — lists the modules, and registers local toolchain plugins |
| `libs.versions.toml` | Project catalog: every dependency the modules share |
| `./kotlin`, `kotlin.bat` | Toolchain wrappers pinning the CLI version |
| `libs/openapi-generator` | Reads an OpenAPI spec, emits models and a typed client with KotlinPoet |
| `libs/shared-common` | What more than one module needs and nothing else: `CoroutineSafeMap`, `KeyedMutex`, `Mailbox`, `CloseGuard`, the keyset-pagination half both stores share, and the one lenient `Json` the storage and messaging libraries read through |
| `libs/shared-amqp` | AMQP over the RabbitMQ client: topology in one block, publishes that wait for the confirm, deliveries as a `Flow`, and a delay-queue retry path |
| `libs/shared-i18n` | Message catalogs compiled once at startup, a per-key walk down the locale chain, ICU arguments and plurals, `Accept-Language` negotiation, and an audit of what each locale is missing |
| `libs/shared-jpa` | Postgres for a Kotlin coroutine service, over Hibernate Reactive: annotated Kotlin entities, sessions confined to the event loop that opened them, HQL, SQL and JPA Criteria — named by `KProperty` rather than by strings — through one suspending builder |
| `libs/shared-kafka` | Kafka for a Kotlin coroutine service: suspending sends, records as a `Flow`, offsets committed after the handler, and an admin client |
| `libs/shared-ktor` | Ktor integrations for the libraries here, a package per integration: a connection per application opened and closed with it, and one negotiated locale per request |
| `libs/shared-koin` | The same seven backends as Koin modules, a package per integration, for callers with no web framework: the container creates the connection and closes it |
| `libs/shared-mongo` | MongoDB for a Kotlin coroutine service: CRUD collection extensions, keyset pagination, an opt-in audit trail, GridFS |
| `libs/shared-redis` | Redis for a Kotlin coroutine service, over Lettuce: a namespaced connection owning one `Json`, and kotlinx-serialized cache, lock, topics and streams |
| `libs/shared-storage` | S3-compatible object storage over the MinIO SDK: buckets, objects, and presigned URLs and upload forms |
| `libs/shared-testing` | Test-only support the libraries share: the backing services their integration specs need, reused from the environment or started as containers for the run |
| `plugins/openapi` | Toolchain plugin wrapping the generator as a build task |
| `examples/demo-api` | Ktor server implementing a slice of `examples/demo-api/openapi.yaml` |
| `examples/demo-client` | Generates a Ktorfit client from that spec and calls the server |
| `examples/demo-spring-client` | Generates a Spring `@HttpExchange` client from the same spec |
| `examples/jpa-shop` | A Ktor catalogue over Postgres showing `shared-jpa`'s CRUD extensions and audit layer |
| `.agents/skills/` | Kotlin Toolchain reference + docs-sync skills (see below) |

A module is a directory with a `module.yaml`, registered by path in `project.yaml`.

## The OpenAPI generator

Two modules and one reference document — read those before changing either module:

- [`docs/openapi-support.md`](docs/openapi-support.md) — what the generator understands of a
  document, and what each part becomes in Kotlin. This is the file that grows.

- [`libs/openapi-generator`](libs/openapi-generator/README.md) — the generator. swagger-parser reads
  the spec into an intermediate representation, and a `SourceEmitter` turns that into KotlinPoet
  files. The root package holds only that IR; `parser` reads, `emit` holds the emitter contract and
  what every emitter shares, `models` emits the schemas, and `ktorfit` and `spring` are the two
  client styles — the parser knows about none of them. The type layer models `allOf`, `oneOf`/`anyOf`
  (discriminated or deduced), enums, `nullable`, `default`, typed `additionalProperties` and the
  common `format`s; inline schemas are promoted to named components in a pass that runs before
  anything else reads the document, so every later stage only ever resolves a name.
- [`plugins/openapi`](plugins/openapi/README.md) — the toolchain plugin around it:
  typed `@Configurable` settings, one `@TaskAction`, and a `generated.sources` entry so the output
  compiles into the consuming module.

A module opts in from its own `module.yaml`:

```yaml
plugins:
  openapi:
    enabled: true
    client: Ktorfit                       # or Spring, or None for models only
    specFile: ../demo-api/openapi.yaml
    packageName: com.strange.demo.client.api
```

Nothing is written to `packageName` itself: interfaces go to `<packageName>.apis`, schemas to
`.models`, and the client machinery — the operation annotation, the exception hierarchy, the plugins
and filters — to `.utils`. Fixed, not configurable, and the reason a document can have both a `tags`
endpoint group and a `Tag` schema.

Everything else has a default: `groupBy: Tag`, `models: Auto`, `interfacePrefix: ""`,
`interfaceSuffix: "Api"`. Grouping by tag turns `categories-controller` into `CategoriesApi`. The
spec's schemas are always generated; only the API surface is optional. `models` decides what binds
them — `Auto` follows the client, and a Ktorfit client is always kotlinx.serialization.

For Ktorfit the generated interfaces are then picked up by `ktorfit-ksp`, which generates the
`createXxxApi()` builders — plugin-generated sources do reach KSP. For Spring there is no
processing step; the interfaces go to `HttpServiceProxyFactory` at runtime.

Both demo apps drive their generated client against the real `demo-api` server over HTTP:
`examples/demo-client` through Ktorfit and kotlinx.serialization, `examples/demo-spring-client` through a
`HttpServiceProxyFactory` proxy and Jackson 3 — which also pins down that a Jackson client and a
kotlinx server read the same document the same way. `HttpServiceProxyFactory` builds an AOP proxy
and formats argument values, so a Spring client module needs `spring-aop` and `spring-context`
alongside `spring-web`; neither arrives transitively. Its conversion service also writes an enum
argument with `Enum.name()`, so a Spring client registers the generated `ApiEnumConverters.kt` with
the factory — without it every enum path, query or header parameter goes out as the Kotlin name.

The generator also reads vendor extensions: `x-kotlin-name` renames anything it would otherwise
derive, `x-kotlin-type` binds a schema to a type the consumer owns, `x-kotlin-value-class` turns a
scalar alias into a `@JvmInline value class`, and `x-kotlin-skip`/`x-internal`,
`x-enum-varnames`/`x-enumNames`, `x-enum-descriptions`, `x-deprecated-reason` and `x-nullable` do
what their names say. **An unrecognised `x-kotlin-*` key fails the parse**, naming the nearest key
that exists; everything outside that namespace is ignored, because it belongs to another toolchain.

It also reads the half of an operation that is not the happy path. Every non-2xx response becomes
a typed failure: one generated exception per error *schema*, so `ErrorResponseException` carries a
parsed `ErrorResponse`, with a base `ApiException(status, rawBody)` for a status the document did
not declare, one it declared without a body, and a body that does not parse. `securitySchemes` and
`security` become `ApiAuthConfig`, one suspending credential slot per declared scheme, resolved
against the document root — `security: []` on an operation means *no* credential, not the root's.

Both need the same thing: the operation has to reach the HTTP layer, where the status and the body
live but the operation is anonymous. Every generated function therefore carries `@ApiOperation(id,
security)`, added once in `emit/ApiFile.kt`, and each style reads it its own way — Ktorfit through
`HttpRequest.annotations` in a client plugin, Spring through an `HttpRequestValues.Processor` that
reads the reflective `Method` and puts the operation in a request attribute. **Both were proved
against the running demo server before being written**, which is also how the Spring default mapper
turned out to need `findAndAddModules()`: without it Jackson binds a generated data class to an
object whose every property is null.

The wiring is generated but installed by the consumer, because the consumer owns the HTTP client —
`install(ApiErrors)` / `install(ApiAuth)` for Ktorfit, `apiErrorFilter()` / `apiAuthFilter()` plus
`apiOperationProcessor()` for Spring. The same split as `ApiEnumConverters.kt`.

## Instruction files

- **`AGENTS.md`** (this file) — the shared, tool-agnostic instructions. Codex, Cursor, Gemini CLI, Zed, Aider and friends read it natively.
- **`CLAUDE.md`** — Claude Code's entry point; it points here and adds only Claude-specific notes.
- **`.agents/skills/`** — skills in the [Agent Skills](https://agentskills.io) `SKILL.md` format, at the cross-client convention path. `.claude/skills` is a symlink to it so Claude Code (which scans its own directory) sees the same files; there is one copy, not two.

## Skills

The skills in `.agents/skills/` carry this repo's working knowledge; use them instead of reasoning from memory:

- **`kotlin-toolchain`** — manifest schema, catalog and template rules, commands, plus `references/`: a markdown cache of the full official documentation (50 pages, version recorded in `references/INDEX.md`).
- **`ktorfit`** — the Ktorfit HTTP client, including how it is wired up here through KSP alone, without its Gradle plugin.
- **`skill-from-docs`** — builds and refreshes docs-backed skills. Each such skill declares its source in a `docs-source.json`; refresh one with:
  ```bash
  python3 .agents/skills/skill-from-docs/scripts/fetch_docs.py --skill <name>
  ```
  Run it after a version bump, or whenever a cached page disagrees with the tool. Files under `references/` are generated — fix the script, not the output.
- **`large-feature-branch-workflow`** — two-level branching for work too large for a single PR.

Skills are budgeted: a `description` is in context every session (keep it ≤250 chars), a SKILL.md body loads on activation (≤~120 lines), and `references/` pages load only when opened. Put cost in the deepest tier that can hold it.

## Commands

The toolchain finds the project by walking up from the working directory, so these work anywhere inside the repo.

```bash
./kotlin build                      # compile + link everything
./kotlin build -m <module>          # one module (repeatable)
./kotlin build -v release           # debug is the default variant
./kotlin test                       # run all tests
./kotlin check                      # run all checks; ./kotlin show checks lists them
./kotlin run -m <module>            # run an application module
./kotlin publish <repository-id>    # e.g. mavenCentral, or an id from the repositories list
./kotlin clean                      # drop build/ and project caches
```

Running a single test:

```bash
./kotlin test --include-test com.example.MyTest.myTestMethod
./kotlin test --include-test 'com.example.MyTest/Nested.myTestMethod'   # '/' separates nested classes
./kotlin test --include-classes 'com.example.*ServiceTest'              # wildcard pattern, repeatable
```

Inspecting the resolved project model — cheap, and it catches manifest errors without a compile:

```bash
./kotlin show modules                    # module names accepted by -m
./kotlin show settings -m <module>       # effective config after templates merge
./kotlin show dependencies -m <module>   # proves a dependency actually resolves
./kotlin show tasks
```

### Toolchain wrapper

`./kotlin` and `kotlin.bat` are committed wrappers pinning the toolchain to the `kotlin_cli_version` at the top of the script (0.12.0). **Use `./kotlin <command>`, not a bare `kotlin`**, so everyone builds with the same version regardless of what is on `PATH`. Regenerate with `kotlin update -c` (add `--target-version=<v>` to move the pin).

### Shared code

`libs/shared-common` holds what **more than one module** needs, expressed without knowing anything
about any of them. It depends on kotlinx-coroutines and kotlinx-serialization and on nothing else,
ever: the moment something in there knows what a topic or a collection is, every library depending
on it inherits that, and a shared module that depends on everything is a cycle waiting for its
second commit.

Look there before writing a helper, and move one there when a *second* caller appears — not in
anticipation of one. A helper with a single caller belongs next to it, where it can be read
alongside the code that explains why it exists.

The three concurrency types are not interchangeable, and the question that separates them is who is
calling: `CoroutineSafeMap` when every caller is a coroutine and each operation stands alone,
`KeyedMutex` when the work behind a key suspends and only callers wanting the *same* key should
wait, and `Mailbox` when a caller is not a coroutine at all — a Java listener or a driver's callback,
which cannot take a mutex and must not be made to block. `libs/shared-common/README.md` has the
reasoning; the short version is that reaching for `runBlocking` to get out of the third case is how
a client deadlocks against its own I/O thread.

**A data-access library offers extensions, not a base class to inherit from.** Neither
`shared-mongo` nor `shared-jpa` has a repository or a CRUD service class; the create/read/update/
delete vocabulary is extensions on `MongoCollection<T>` and on the JPA session. Two reasons, and the
first is the one that decides it: an extension takes `T` from its receiver or reifies it at the call
site, while a class cannot have a `reified` type parameter and so has to be handed a `KClass` or a
property reference to read an owner off — `session.findAll<Purchase>()` against
`JpaRepository(Purchase::id).findAll(session)`. And a repository over a collection turned out to be
one-line delegation twenty times over, with the overridable hooks its service used being the least
reusable part of either module. What a base class earned and an extension still has to provide is
kept explicitly: `insertAndRead`, the transaction guard on every JPA write verb, and the audit
stamps. When a new store is added, follow the same shape.

**Shared does not mean everything shared goes there.** `libs/shared-i18n` is used by more than
one module and is still its own library, because ICU4J is a 15 MB jar and `shared-common`'s rule
is kotlinx-and-nothing-else — putting message formatting in it would make `shared-kafka` carry a
formatting library it will never call. The same test applies to the next candidate: if it brings
a dependency, it brings that dependency to everything.

Framework integrations go in `libs/shared-ktor`, **one package per integration** — i18n, Redis,
Mongo, AMQP, Kafka and object storage. An application wires them together in one `install` block and
should read them from one dependency. `libs/shared-koin` is the same seven as Koin modules, for the
callers that have a container and no web framework; it knows the container, `shared-ktor` knows the
framework, the libraries know the backends, and none of them knows two.

**Every backend is declared `compile-only`, including the ones this module's API returns.**
`call.redis` hands back a `Redis` and Lettuce still stays off a consumer's runtime classpath, which
sounds wrong and is not: an application that installs `RedisConnection` already depends on
`shared-redis`, because `RedisConfig` is the only way to configure the plugin at all — and one that
installs only `I18n` never loads a class from any of the others, so nothing is missing when
nothing is linked. It is self-enforcing rather than a convention to remember. Verified with
`./kotlin show dependencies -m shared-ktor`: a compile-only entry sits in the COMPILE scope and is
absent from RUNTIME.

The tests are the other half: they need the real libraries at runtime, so `test-dependencies`
carries each of them again at normal scope, plus `//libs/shared-testing` for the servers to talk to.
Every plugin is specced against a real backend, because "one connection, closed on stop" is not
observable from a mock.

The plugins are not in the libraries they wrap because `shared-i18n` and `shared-redis` have callers
with no server in them — a worker, a CLI, a consumer. The library knows the backend, `shared-ktor`
knows the framework, and neither has to know both.

**A framework integration must assume the resource is not its own, and must not be the only way to
reach it.** Three rules, and the next integration is built to them rather than retrofitted:

- **Take an instance as well as a config.** Every plugin's configuration has an `instance`; set it
  and the plugin adopts what an application or a container already built, instead of opening a
  second one.
- **Whoever created it closes it.** `Resources.kt` says this once, as `own` (we opened it, we close
  it on `ApplicationStopped`) and `publish` (someone else's, we leave it alone). A plugin that
  adopts a connection and also closes it is the second close.
- **Reaching a resource only through `call.x` is a service locator.** A class a container builds
  has no `ApplicationCall`, so each plugin can register what it installed —
  `install(RedisConnection) { config = …; injectable = true }` — and the same connection is then
  both `call.redis` and a constructor parameter. `injectable` is off by default because
  `ktor-server-di` is compile-only, and each `provideX` lives in its own file so nothing loads a
  class from Ktor's DI until it is switched on.

**And close idempotently, through `CloseGuard`.** A resource that is handed around is closed more
than once, and the rule above says who *should* close it, not what happens when two of them do.
Ktor's DI closes every `AutoCloseable` it hands out at application stop — one a provider merely
passed through included, and a per-key `cleanup` runs beside that hook rather than instead of it, so
a library cannot opt out. The drivers do not agree here either: Lettuce and the MinIO client tolerate
a second close, the RabbitMQ client throws. Any new `AutoCloseable` in these libraries closes through
the guard, so that all of it stays a question of tidiness rather than of correctness.

### Local services

The databases this workspace runs against are **already containerised and usually already up** —
`~/workspace/docker/apps/` holds one compose file per service: `database/mongo` is an `rs0` replica
set on `localhost:27017`, transactions included, `database/redis` is Redis Stack on
`localhost:6379`, `minio` is an S3-compatible store on `localhost:9000`, `kafka` is a three-broker
KRaft cluster, and `rabbitmq` is on `localhost:5672` with its management UI on `15672`. Check `docker ps` before pulling an image or starting a Testcontainers container:
the pull costs a gigabyte and the second container either clashes on the port or silently tests a
different server than the one everything else uses.

**A spec must not depend on the host having the right daemon up.** `libs/shared-testing` declares
each backing service and resolves it in one order: the environment variable if it names a server,
otherwise a container started once for the run, otherwise `available == false` and the spec skips.
Mongo, Redis, AMQP and MinIO all work this way. Declare a new backend in `Backends.kt`, never in a
library's own test tree.

| library | override | without it |
| --- | --- | --- |
| `shared-mongo` | `MONGO_TEST_URI` | `mongo:8`, a single-node replica set |
| `shared-redis` | `REDIS_TEST_URI` | `redis:8-alpine`, on db 15 |
| `shared-amqp` | `AMQP_TEST_URI` | `rabbitmq:4-management` |
| `shared-storage` | `MINIO_TEST_ACCESS_KEY` **and** `..._SECRET_KEY` | `minio/minio:latest` |
| `shared-kafka` | `KAFKA_TEST_BOOTSTRAP` | `confluentinc/cp-kafka:latest`, one broker |
| `shared-jpa` | `POSTGRES_TEST_URI` **and** `..._USER` **and** `..._PASSWORD` | `postgres:18-alpine` |
| `shared-jpa` | `MYSQL_TEST_URI` **and** `..._USER` **and** `..._PASSWORD` | `mysql:8.4` |
| `shared-jpa` | `DB2_TEST_URI` **and** `..._USER` **and** `..._PASSWORD` | nothing — the DB2 specs skip |

**The credentials rule is unchanged; what it costs is not.** `AMQP_TEST_URI` and the MinIO key pair
still have no defaults and must never gain any — a credential with a default is a credential in
source control, and they live in `~/workspace/docker/apps/*/.env`, exported for a run and never
committed. But their absence is no longer a reason to skip: a container hands out credentials of its
own, so the 78 specs in those two libraries now run on a machine where nobody exported anything.
They used to report skipped there and prove nothing.

`MINIO_TEST_ENDPOINT` on its own does not take the override — an endpoint with no way in fails later
and less clearly than a container would. `POSTGRES_TEST_URI` is refused on its own for the same
reason: a Postgres URI does not carry the password, and a driver that connects without one fails at
authentication in a way that reads like a network problem.

**Each `shared-jpa` spec gets a schema of its own**, created before it and dropped `cascade` after
it — the per-spec Mongo database and Redis namespace, in the shape Postgres has for it. It earns its
keep against a real server: a spec creating its tables in `public` would be working among whatever
else lives there, and Hibernate's `create-drop` would take that with it on the way out. The workspace
runs `postgis/postgis:latest` on 5432, which is exactly such a server.

The reuse path is still the fast local loop, and still the seam CI uses to point at a service it
provisioned. A reused server is shared, so everything below about leaving it as you found it applies
to it exactly as before.

**Kafka's container is one broker, and that costs something worth knowing.** The workspace cluster
is three brokers with `min.insync.replicas = 2`, so a topic there has three replicas and
`acks = all` really waits for a quorum; a container gives one replica, so it waits for one broker.
The ack path is exercised either way, the quorum only on the real cluster. Nothing asks for a hard
three any more — `KafkaTestCluster.replicationFactor` asks the cluster what it has, capped at three,
because a topic asking for more replicas than there are brokers is not a weaker test but a refused
`createTopics`.

To exercise the quorum, point at the workspace cluster. Its brokers advertise container hostnames
and publish no host ports, so the names have to resolve first — an address the host can reach is not
enough on its own:

```
# /etc/hosts
172.22.0.115 kafka1
172.22.0.116 kafka2
172.22.0.117 kafka3
```

```bash
KAFKA_TEST_BOOTSTRAP="kafka1:9092,kafka2:9094,kafka3:9096" ./kotlin test -m shared-kafka
```

**An override that does not answer is not quietly replaced by a container.** Naming a cluster and
getting a container instead would be worse than skipping: the run would look green and would have
tested something else. This holds for all five backends.

To take the override and run against the workspace's own broker or object store:

```bash
set -a; . ~/workspace/docker/apps/rabbitmq/.env; set +a
AMQP_TEST_URI="amqp://$RABBITMQ_DEFAULT_USER:$RABBITMQ_DEFAULT_PASS@localhost:5672/%2F" ./kotlin test -m shared-amqp

set -a; . ~/workspace/docker/apps/minio/.env; set +a
MINIO_TEST_ACCESS_KEY=$MINIO_ROOT_USER MINIO_TEST_SECRET_KEY=$MINIO_ROOT_PASSWORD ./kotlin test -m shared-storage
```

The `%2F` there is the default virtual host and not decoration — a plain trailing `/` is the *empty*
vhost, which the broker refuses with a message about permissions that says nothing about the cause.
The container's URI carries no vhost path at all and sidesteps it.

A run that reuses a server has to leave it as it found it, because it is not theirs: the Mongo specs use a
database per spec and drop it, the Redis specs use database 15 with a key namespace per spec and
delete it, the storage specs create a bucket per scenario and empty and remove it, and the Kafka
specs create only the topics and groups they delete, and the AMQP specs name every exchange and
queue uniquely per run and delete them. Nothing here calls `FLUSHDB`, and nothing touches a bucket,
a topic or a queue it did not create.

Keep all of that even where a container made it unnecessary. Against a container, a spec that fails
to clean up after itself is a spec whose next run behaves differently — the isolation is what makes
that visible, and `FLUSHDB` is what would hide it.

## Module layout

Sources in `src/`, tests in `test/`, test-only resources in `testResources/`:

```yaml
# libs/<name>/module.yaml
product: jvm/lib          # or kmp/lib, jvm/app, ...

apply:
  - //common.module-template.yaml   # shared config, see below

dependencies:
  - //libs/core                     # another module — path from the project root
  - $libs.kotlinx.serialization     # project catalog alias

test-dependencies:
  - $libs.kotest.runner.junit5
```

Register modules in `project.yaml` by path (explicit entries are what the docs recommend; globs such as `libs/*` also work):

```yaml
modules:
  - libs/core
  - libs/api
```

Rules that are easy to get wrong:

- **Module dependency paths start with `//`** and are relative to the project root. A bare `libs/core` is read as an *external* Maven coordinate; relative forms (`./nested`, `../sibling`) work but the docs warn they may be deprecated.
- `module.yaml` is schema-validated: an unknown property fails the command with a pointer to the offending line, so a passing `kotlin show modules` is a cheap syntax check after editing a manifest.
- Dependencies are **not transitive at compile time** — a dependent module sees a library only if the producing module marks it `exported: true` (Gradle's `api()`).
- Tests use kotlin-test with **JUnit 5** by default on JVM/Android; change via `settings.junit`.

## Dependency reuse

`libs.versions.toml` at the repo root is the project catalog and **the single place where dependency versions live**. Any dependency used by more than one module — and by default any dependency at all — goes in the catalog and is referenced from modules as `$libs.<alias>`; do not paste versioned Maven coordinates into a `module.yaml`. Alias keys map with dashes becoming dots: `kotest-runner-junit5` → `$libs.kotest.runner.junit5`. Toolchain-provided catalogs (`$kotlin.*`, `$compose.*`) need no catalog entry.

**The toolchain does not read `[bundles]`** — `$libs.bundles.<name>` fails with `No catalog value for the key`. The bundles in this catalog still document which stack a dependency belongs to and serve Gradle-based consumers, but modules must list individual aliases.

To share a dependency *set* or settings across modules, use a **module template**: a `<name>.module-template.yaml` with the same shape as `module.yaml` (minus `product:`), pulled in with `apply: [ //name.module-template.yaml ]`. Templates merge dependencies, settings, and repositories, and can apply other templates; check the result with `kotlin show settings -m <module>`.

The catalog's `[bundles]` groupings map to the intended consumer surfaces of this library, which is the fastest way to see which stack a new module belongs to:

- **`compose`** / `compose-test` / `compose-android-test` / `compose-ksp` — Android + Compose client: Material3, Navigation/Compose Destinations, Room, DataStore, WorkManager, Koin, Apollo, kotlinx-rpc client.
- **`ktor`** / `ktor-mix` / `ktor-test` — server: Ktor, kotlinx-rpc server, MongoDB Kotlin coroutine driver, Koin, Konform validation, Nimbus JOSE JWT, Spring Security crypto, simple-java-mail, Kotest.
- **`shared`** / `shared-test` — code common to both: kotlinx-serialization, kotlinx-rpc client, bson-kotlinx.
- **`kotlinx`**, **`faker`** — coroutines/datetime, and kotlin-faker for test data.

**A module that turns on `settings.ktor` pins the version to the catalog's.** `ktor: enabled` gives
`$ktor.server.core` and the rest from the toolchain's *own* default version, which is 3.5.2 today
and matches `ktor = "3.5.2"` in the catalog by coincidence rather than by construction — a toolchain
upgrade would move one and not the other, and an artifact this repo names itself (`ktor-server-di`,
on `version.ref = "ktor"`) would then be a different Ktor from `ktor-server-core`. So:

```yaml
settings:
  ktor:
    enabled: true
    version: 3.5.2   # matches `ktor` in libs.versions.toml
```

`./kotlin show settings -m <module>` says which one is in force: `# module.yaml` when it is pinned,
`# default` when the toolchain is choosing. Three modules enable it — `shared-ktor`, `demo-api`,
`demo-client` — and all three carry the pin.

The catalog's `kotlin = "2.4.0"` entry is for consumers that need an explicit Kotlin version; the toolchain supplies its own compiler and stdlib (2.4.10 with CLI 0.12.0), so that entry does not control what this repo compiles with.

## Coroutine-first Kotlin

Every library here wraps a blocking, callback-driven Java client. The shape that keeps working is
the same each time, and the mistakes are the same each time too.

- **Blocking calls go on `Dispatchers.IO`, and everything in these clients blocks.** A declare, a
  poll, a send, a `basicPublish` — each is a round trip however small it looks. A blocking call left
  on a caller's dispatcher is a thread the rest of the application needed.
- **Prove the client's threading rule before designing around it.** Two of these libraries were
  built on a guess that turned out to be wrong. `KafkaConsumer` was assumed to be thread-*affine*
  and was given a dedicated thread; `ConsumerConfinementTest` showed it wants *exclusion during a
  call*, so `Dispatchers.IO.limitedParallelism(1)` does the job and the thread went. `AmqpPublisher`
  carried a `@Volatile` between two callbacks; `ConfirmThreadsTest` showed both arrive on one
  connection thread, so it was insuring against nothing. Both specs need no server and run in
  milliseconds. Write the spec, then design.
- **A callback that cannot suspend gets a `Mailbox`, never `runBlocking`.** There is no other
  correct bridge: a `Mutex` may suspend and a callback may not, and `runBlocking` on a client's own
  I/O thread parks the thread it needs for heartbeats and deliveries. See the shared code section
  for which of the three concurrency types fits which caller.
- **Prefer a single owner to a lock.** One coroutine draining a mailbox owns everything the messages
  touch, so the state under it is plain `var`s and plain maps — no `@Volatile`, no concurrent
  collection. It also buys what no pair of guarded flags can: two facts that must be applied in
  order *are*, because a channel is FIFO. Reach for a concurrent collection only after establishing
  that a single owner will not do.
- **Never hold a lock across suspending work.** A mutex held while a loader runs turns *n*
  concurrent loads of *n* different keys into one queue. `CoroutineSafeMap.getOrPut` takes a value
  rather than a loader for exactly this reason; `KeyedMutex` is the type for when the work suspends.
- **When a foreign API forces a real thread, make it virtual.** Coroutines first, as everywhere
  else here — but `Runtime.addShutdownHook` takes a `Thread` and there is nothing to negotiate.
  Then it is `Thread.ofVirtual()`, never `Thread(…)`: a few hundred bytes against a megabyte of
  committed stack, and blocking parks a continuation instead of an OS thread. On the JDK 25 this
  repo runs, JEP 491 removed the `synchronized` pinning that used to be the argument against them.
  Mind which half of the API you take — `Thread.startVirtualThread` starts on the spot, so a hook
  built with it is registered already-dead (never runs) or refused as still-alive (and then the
  whole holder fails to initialise), and **both outcomes are swallowed without a word**. The form
  that works is `Thread.ofVirtual().unstarted { … }`. `ContainerService` carries the scar and the
  spec that would have caught it.
- **A loop that never suspends starves its own dispatcher.** `KafkaSubscriber`'s poll loop owns its
  dispatcher for the whole poll timeout and has no suspension point between turns, so anything that
  tried to `withContext(thatDispatcher)` waited forever — a real deadlock, found by a spec that hung
  for ten minutes. Work reaches such a loop as a message it applies on its next turn, never as a
  call that waits to be scheduled.

## Performance

- **One client per configuration, not per call.** A Lettuce connection multiplexes and is
  thread-safe, a `KafkaProducer` batches across callers and holds connections to the whole cluster,
  and an AMQP connection carries any number of channels on one socket. Two of any of them halves the
  batching and doubles the sockets. What *does* need one each is the thing whose state is
  per-conversation: an AMQP channel per publisher and per consumer, because delivery tags and
  confirm sequence numbers mean nothing anywhere else.
- **Batch by enqueueing in order and awaiting together.** `sendAll` and `publishAll` hand the whole
  collection to the client and then await the acknowledgements, rather than launching a coroutine
  per record. One coroutine each gives up the ordering these APIs promise and buys nothing, since
  the client batches either way — this was a bug before it was a rule.
- **Backpressure is a default, not an option.** Both consumers bound what may sit between the broker
  and the handler — `SubscriberOptions.prefetch` at 64, `ConsumerOptions.prefetch` at 32 — because
  the protocols' own default is *everything*: one consumer holding a queue's worth of messages in
  memory while its peers hold none. In Kafka the loop also keeps polling while paused, since not
  polling is what gets a consumer evicted mid-batch.
- **Say what a query loads. Entity associations are `LAZY`, and what a caller needs is fetched.**
  Hibernate Reactive has no transparent lazy loading — there is no thread to block on the second
  select — so an unfetched `LAZY` association throws when it is read, inside the session as readily
  as after it, and the tempting fix of leaving associations `EAGER` is the N+1 wearing a different
  hat: three rows pointing at three different owners cost three secondary fetches with JPA's
  `@ManyToOne` default and none with `fetch(…)`. `FetchJoinTest` counts both off Hibernate's own
  `entityFetchCount`, which is the counter to reach for — `prepareStatementCount` reads zero,
  because there is no JDBC under the Vert.x pool. So: annotate every association `LAZY`, name what
  the query needs with `fetch` / `fetchEach`, and where the caller only reads a few columns, project
  instead and load no entity at all — Hibernate packages any result class with a matching
  constructor, so `query<Summary>("select a, b from …")` needs no constructor expression. Where there
  is no query to join on — `find` and a stateless `get` —
  the answer is an entity graph:
  `session.entityGraph<Purchase>().add(…)` is a fetch plan that is also a value, so a `find` and a
  query cannot disagree about what they load. `docs/jpa-criteria.md` has the rules for both,
  including the one nothing enforces: `limit` and `offset` silently truncate whenever a query loads a
  collection, whether by `fetchEach` or by a graph naming one.
- **Keep the fast tests fast and the slow ones optional.** Timing claims — a full buffer pausing, a
  strategy committing when it says it does, partitions running concurrently — belong on Kafka's own
  `MockConsumer`/`MockProducer` and run in about two seconds. A real server is for behaviour that
  *is* the server's: an acknowledgement removing a message, a TTL expiring one. Those specs skip
  when the server is unreachable, so a machine without it reports skipped rather than red.
- **Watch what the machine is carrying.** The workspace's containers do not all fit at once: the
  three-broker Kafka cluster is around 1.9 GiB, and starting it alongside everything else once drove
  this machine into the OOM killer, which chose the running IDE. Check `docker ps` first, stop what
  you started, and prefer the specs that need nothing running.

## Conventions

- **Formatting is ktlint's job**, configured by `.editorconfig` at the repo root (wildcard imports
  are allowed there; everything else is ktlint's `ktlint_official` style). `./kotlin check` does
  *not* run it — `./kotlin show checks` lists only `tests` — so run it yourself before committing,
  excluding generated output:

  ```bash
  ktlint --relative "**/*.kt" "!build/**"        # report
  ktlint -F  --relative "**/*.kt" "!build/**"    # fix what it can
  ```

  Without the `!build/**` exclusion it lints KSP and plugin output and drowns you in thousands of
  violations in files nobody edits. The `filename` rule is the one `-F` cannot fix: a file's name
  must be PascalCase, so `Main.kt`/`DemoServer.kt`, never `main.kt`.
- **Documentation is written in the same change as the code**, not collected at the end. A branch
  that adds a capability adds its paragraph; a branch that changes a behaviour edits the paragraph
  that described the old one. A "what it does not handle" list that still describes a previous
  phase is worse than no list.
- **Each documentation file has one audience, and they do not mix.**

  | File | Answers |
  | --- | --- |
  | `README.md` | What is this repo, and where do I read next? Stays short. |
  | `docs/openapi-support.md` | What does the generator understand of an OpenAPI document? **This is where support for a new keyword, format or extension is documented** — it is the part that grows every phase. |
  | `libs/openapi-generator/README.md` | How is the module shaped, what does each emitter produce, how do I add one? Roughly constant in size. |
  | `plugins/openapi/README.md` | How do I turn this on in a module, and what does that need on its classpath? |
  | `libs/shared-common/README.md` | What belongs in the shared module, and which of the three concurrency types a given caller wants |
  | `libs/shared-amqp/README.md` | The same, for AMQP — topology, confirms, prefetch, and why a retry is a queue nobody consumes |
  | `libs/shared-i18n/README.md` | The same, for i18n — the locale walk, what eager compilation buys, and why `ResourceBundle` is not underneath it |
  | `libs/shared-ktor/README.md` | The Ktor integrations — what each plugin owns and closes, and how one module holds them all without becoming a fat dependency |
  | `libs/shared-koin/README.md` | The Koin modules — which side creates the connection, which adopts it, and why two of them have no `onClose` |
  | `libs/shared-jpa/README.md` | The same, for Postgres — the confinement rule the library is built around, and why entities need two compiler plugins. Roughly constant in size |
  | `docs/jpa-criteria.md` | What a shared-jpa query may say — the operators, joins, fetch joins, entity graphs, projections, function vocabulary and the two escapes. **This is where a new operator or function is documented** |
  | `docs/jpa-mapping.md` | What a shared-jpa entity may say — the database, column naming, identifiers, `Instant`/`Uuid`, JSON columns, validation. **This is where a new `SqlTypes` code, strategy or converter is documented** |
  | `libs/shared-kafka/README.md` | The same, for Kafka — the publisher, the poll loop, and why the loop is shaped the way it is |
  | `libs/shared-mongo/README.md` | How is the Mongo library shaped, and why is each non-obvious part the way it is? |
  | `libs/shared-redis/README.md` | The same, for Redis — including what each layer deliberately does not do |
  | `libs/shared-storage/README.md` | The same, for object storage — and what a presigned URL can and cannot promise |
  | `libs/shared-testing/README.md` | Where an integration spec's server comes from, and how a container declared there is cleaned up |
  | `AGENTS.md` | How do I work in this repo? One paragraph per capability, never the detail. |

  When a README section starts growing every phase, that is the signal it belongs in `docs/`, not
  the signal to keep appending. `libs/openapi-generator/README.md` reached 394 lines before its
  reference half moved out; splitting on *audience* rather than on length is what made the seam
  obvious. `libs/shared-jpa/README.md` reached 921 and split the same way, into the query vocabulary
  and the mapping vocabulary — the two halves that grow — leaving the reasoning behind.
- **Keep files short and single-purpose.** One file holds one concern; when two things could be
  separated cleanly, separate them. A file growing past roughly 150 lines is a signal to split it,
  not a threshold to argue with — split by responsibility, never by line count.
- **Follow SOLID, strictly.** In practice, here:
  - *Single responsibility* — the parser reads the spec, an emitter shapes output, the plugin wires
    it into the build. None of them does another's job.
  - *Open/closed* — a new client style is a new `SourceEmitter` and one `ClientKind` value; it must
    not require editing the parser or the existing emitters.
  - *Liskov* — every `SourceEmitter` is usable wherever the interface is, including the models-only
    one; no implementation may need special handling by its caller.
  - *Interface segregation* — keep interfaces narrow. `SourceEmitter` is one method because that is
    all a caller needs.
  - *Dependency inversion* — depend on the abstraction: the plugin's task action talks to
    `SourceEmitter`, and picks the implementation in exactly one place.
- **Tests are kotest `FeatureSpec`, grouped by scenario.** Every spec extends `FeatureSpec`, with
  `feature("...")` naming the behaviour under test and `scenario("...")` naming one case of it:

  ```kotlin
  class OpenApiParserTest :
      FeatureSpec({
          feature("grouping") {
              scenario("groups operations by tag, one interface per tag") { /* ... */ }
              scenario("falls back to the path segment when a tag is missing") { /* ... */ }
          }
      })
  ```

  Features are the unit of grouping, so a spec that would hold a single flat list of tests is
  telling you the feature names are missing, not that grouping does not apply. Nest a `feature`
  inside a `feature` when a case genuinely has sub-cases; do not reach for `context`, which belongs
  to the other spec styles. One spec class per file, named after the file.
- **Every module's packages start with `com.strange`.** The rest follows the module: `com.strange.openapi` for `libs/openapi-generator`, `com.strange.openapi.plugin` for `plugins/openapi`, `com.strange.demo.api` for `examples/demo-api`. Generated code follows the same rule — the `openapi` plugin's `packageName` setting is set per module, and defaults to `generated.api` only when nobody sets it.
- **Organise by package, not as a flat pile of files — `test/` exactly as much as `src/`.** A module
  with more than one concern gets a directory per concern, and the directory matches the package —
  `src/parser/` is `com.strange.openapi.parser`. The root package holds only what every package
  depends on: the shared contract, nothing else. When a file lands in the root because it did not
  obviously belong anywhere, that is the signal a package is missing.

  A test tree is not exempt, and it is where this slips: a fixture gets written beside whichever spec
  needed it first, and three specs later the fixtures are scattered across four packages with no rule
  anyone could state. **Test fixtures live in a package named for what they are, not for the spec that
  happened to need them first** — entities in `test/entity/`, and the same for any other family of
  fixture a module grows. A spec imports its fixtures; it does not host them. `shared-jpa`,
  `shared-ktor` and `shared-koin` all keep their JPA entities in `…entity`.

  One exception, and it has to be argued in the file: a spec that is *about* a package boundary owns
  the package it scans. `shared-jpa`'s `EntityScanTest` needs a package holding nothing but the
  classes it expects to find, which is why those fixtures sit in `test/entity/scan/` instead of
  beside the rest.
- `.gitignore` excludes `build`, `.idea`, and `.jbeval`; build output goes to `build/` under the project root unless `--build-dir` overrides it.
- **`develop` is the integration branch and every PR targets it.** Branch off `develop`, open the
  pull request against `develop`, and merge it there. Nothing is merged directly into `main`, however
  small and however green — a PR opened against `main` has the wrong base and wants recreating, not
  merging.
- **`main` is aligned from `develop`, only when that is asked for.** Aligning is not part of finishing
  a feature: it happens when someone asks for it, and it is `git checkout main && git merge develop`
  — never merging a feature branch into `main`, never cherry-picking across. That fast-forwards while
  it can and leaves a `Merge branch 'develop'` commit once it cannot, which is the same shape
  `nxgt-federation` and `sellix-monorepo` carry on their own `main`.

  If `develop` is *behind* `main`, someone has written to `main` directly and that is the thing to fix
  first: merge `main` into `develop`, then align `main` from the result. No history is rewritten
  either way.

  The failure this prevents is quiet: merging features into `main` while `develop` sits behind works
  perfectly until `develop` carries real work of its own, and then the two have genuinely diverged
  with no single branch holding everything. It has already happened once here — three PRs landed on
  `main` while `develop` was nine commits behind, and it was harmless only because `develop`'s three
  extra commits were merges carrying no file changes at all.
