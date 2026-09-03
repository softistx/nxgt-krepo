# What a stx-spring-boot application may configure

Every `stx.*` key, what it switches on, and what it costs. This is the half of `libs/stx-spring-boot`
that gains an entry every phase — a key per capability, and roughly five per new integration — so it
lives here rather than in the module README, which answers *why the integration is shaped this way*
and stays roughly the size it is.

[`libs/stx-spring-boot/README.md`](../libs/stx-spring-boot/README.md) has the reasoning.
[`docs/spring-mongo-queries.md`](spring-mongo-queries.md) is the other half of this one — what a
query may say.

## Four rules that apply to every key on this page

**Nothing is on by default.** Every `enabled` below defaults to `false`, and every
`@ConditionalOnProperty` behind them is written without `matchIfMissing`. Putting `stx-spring-boot`
on a classpath starts nothing, opens no connection and registers no filter. The one thing that does
happen is that a package guarded by `@ConditionalOnClass` becomes reachable when the application
adds that library itself.

**A `defaultValue` of — below means the key is genuinely required when its feature is enabled**, and
the failure names the key: `stx.mongo.enabled is true but stx.mongo.uri is not set`. That is a
deliberate alternative to a binder error naming a constructor parameter, and to a default that would
be a guess about somebody's cluster or — worse — a credential in source control.

**Durations are `java.time.Duration`**, spelled `30s`, `500ms` or `PT30S`. Spring's binder has never
heard of `kotlin.time.Duration`, so a property written the Kotlin way binds correctly only while
nobody sets it; each integration converts at the boundary.

**Where a `stx.*` key overlaps one of Spring Boot's own, Boot's wins.** A `stx.*` key is a better
default than the framework's, never an override of what the application asked for by name. There are
only two places on this page where the two describe the same bean, and both are pinned by a spec
rather than left to ordering:

| `stx.*` | Spring Boot | Who wins |
| --- | --- | --- |
| `stx.i18n.languages` / `.fallback` | `spring.web.locale` / `spring.web.locale-resolver` | Boot's, when either is set — `stx.i18n`'s resolver stands down and only the catalogs load |
| `stx.data.mongo.enabled` | `spring.data.mongodb.representation.big-decimal` | Neither: the `MongoCustomConversions` bean is this module's, and it reads Boot's property and applies it |

Everything else that looks like an overlap is not one. `stx.mongo`, `stx.redis`, `stx.kafka`,
`stx.amqp` and `stx.jpa` configure the **`stx-*` library's own client**, which is a different object
from the one `spring.mongodb`, `spring.data.redis`, `spring.kafka`, `spring.rabbitmq` and
`spring.datasource` configure. (`spring.mongodb` and not `spring.data.mongodb`: Boot 4 split them,
leaving `spring.data.mongodb` with GridFS and the representation above, and a stale
`spring.data.mongodb.uri` binds to nothing without warning.)

Setting both gives an application two clients, not one configured twice — which is a legitimate
thing to want and an expensive thing to do by accident.

---

## The web layer

### `stx.errors`

Translated failures in one response shape. Needs a `Messages` bean — declared by the application, or
contributed by `stx.i18n` below. There is deliberately no fallback that skips translation: a body
reading `orders.not-found` in production is worse than a context that refuses to start.

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Registers `ApiExceptionHandler`, and `ValidationExceptionHandler` when Jakarta Validation is present |
| `include-debug-message` | boolean | `false` | Puts `ApiException.debugMessage` in the response. What a thrower calls a debug message is routinely a query, a constraint name or an upstream body |

### `stx.json`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Replaces WebFlux's Jackson codecs with kotlinx.serialization, as a `CodecCustomizer` |
| `explicit-nulls` | boolean | `false` | Writes `null` properties instead of omitting them |
| `encode-defaults` | boolean | `true` | Writes properties equal to their default |

Off by default, and that is not timidity: Jackson serializes anything, kotlinx serializes what carries
`@Serializable` and throws on the rest. Switching a running application over is a decision with a
blast radius.

The two settings interact, which is worth knowing before someone turns on `explicit-nulls` and finds
their nulls still missing: a property equal to its default is dropped first, and for a
`String? = null` the default *is* null.

### `stx.cors`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Registers the CORS filter |
| `origins` | list | `[]` | Exact origins. Empty by default — a browser policy that arrives already permitting somebody is the wrong shape of default |
| `origin-patterns` | list | `[]` | Pattern-matched origins. The one that works with credentials |
| `methods` | list | `["*"]` | |
| `headers` | list | `["*"]` | Request headers the origin may send |
| `exposed-headers` | list | `[]` | Response headers the browser may read. Empty means the CORS-safelisted ones |
| `allow-credentials` | boolean | `true` | Whether cookies and `Authorization` may be sent |
| `max-age` | long | `3600` | Seconds a browser may cache the preflight answer |
| `path` | string | `"/**"` | |

Everything after `origins` defaults permissively on purpose: once an origin is trusted, restricting
which methods it may use adds nothing an attacker at that origin cannot work around.

**One combination fails at startup deliberately.** `origins: ["*"]` with `allow-credentials: true` is
forbidden by the CORS specification, and Spring throws when the *request* arrives rather than when
the bean is built — turning a configuration mistake into an intermittent browser failure found by
whoever is testing the front end. The policy refuses it while the context is starting and names
`stx.cors.origin-patterns`, which is what actually does the job.

These keys become a `com.softistx.common.http.CorsPolicy`, which is also what `stx-ktor` installs
Ktor's plugin from — so an application moving between the two frameworks keeps its origins and their
meaning.

### `stx.security`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Registers a `PasswordEncoder` and `AnnotationTemplateExpressionDefaults` |
| `bcrypt-strength` | integer | `10` | BCrypt cost factor |

**Two things have to be switched on for `@RequireRole` to work**, and only one of them is here.
Without `enabled`, `{value}` is never substituted and `@RequireRole("ADMIN")` denies every call as the
literal expression `hasRole('{value}')`. And `@EnableReactiveMethodSecurity` stays the application's
to add, because turning method security on changes how every bean in the context is proxied — not
something a dependency should do quietly.

The filter chain, the permitted paths and the authentication manager are application policy. A
library that guessed at them would either lock a service out of its own health check or open
something that should not be.

### `stx.i18n`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Loads the catalogs and narrows the locale resolver |
| `languages` | list | `["en"]` | The languages with catalogs — `[en, fr]`, or `[en, fr-CA]` for a region |
| `fallback` | string | `"en"` | The language a key falls back to. Per key, not per request |
| `base-name` | string | `"locales/messages"` | `locales/messages_fr.properties` for `fr` |
| `fail-on-missing-key` | boolean | `false` | Throw instead of returning the key |

Turn `fail-on-missing-key` on in a test suite and leave it off in a deployment: a running server
should not fail a request over a translation, and a test suite should not pass over one.

It also replaces WebFlux's `LocaleContextResolver` with one that only answers with a language it has.
The default answers with whatever `Accept-Language` asked for, so a browser asking for Japanese
produces a `Translator` for Japanese that falls back key by key.

**Unless the application set `spring.web.locale` or `spring.web.locale-resolver`**, in which case
Boot's resolver stays in charge and only the catalogs load here — the rule at the top of this page.
Both halves of that are ordering-sensitive and both are pinned by `LocaleResolverPrecedenceTest`:
before it existed, Boot's resolver won in every arrangement, including with no `spring.web.*` set,
and these two keys configured a bean that never reached a request.

---

## Spring Data Mongo

### `stx.data.mongo`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Contributes the `Instant` converters to Spring Data |
| `gridfs-bucket` | string | — | Registers a `ReactiveGridFsTemplate` on this bucket |
| `transactions` | boolean | `false` | Registers a `ReactiveMongoTransactionManager`. Needs a replica set |
| `auditor` | boolean | `false` | A `ReactiveAuditorAware` so `@CreatedBy` fills itself in. Needs Spring Security |
| `create-indexes` | boolean | `false` | Creates the indexes mapped entities declare, after startup |

`create-indexes` **never drops.** The version this replaced dropped every index on every collection
and rebuilt them at each startup, which is an outage waiting for a large collection: while an index
rebuilds every query that used it scans, once per instance on a rolling deploy. Creating an index
that already exists is a no-op, so create-only is idempotent — and an index no longer declared is
left alone, because deciding it is unused is a migration's job.

`auditor` needs `@EnableReactiveMongoAuditing` in the application, which stays the application's for
the same reason method security does.

### `stx.data.mongo.audit`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Records every save and delete of an `@Auditable` document |
| `collection` | string | `"audits"` | Changing it after entries exist starts a new, empty history |

Not to be confused with `stx.data.mongo.auditor`, one letter away: that one stamps *who* onto the
document, this one keeps the document's whole history in a collection of its own.

`stx.data.mongo` used to have a `migration` group. It is gone: migrations are
[`stx.migrations`](#stxmigrations) now, in `stx-migrations-spring`, for both stores and both stacks.
What `stx-spring-boot` still contributes is a coroutine `MongoDatabase` bean built from the
`ReactiveMongoDatabaseFactory` — the bridge `stx.migrations.store: mongo` reads, and the reason an
application on Spring Data does not have to turn on `stx.mongo` and open a second pool to get one.

---

## The stx libraries

One group per library, each `@ConditionalOnClass` so the `compile-only` dependency stays optional at
runtime, and each bean `@ConditionalOnMissingBean` — which is the extension point. A `Json`, a
`ConnectionFactory` and a `MongoClientSettings.Builder` are not strings, so the settings this module
has no opinion about are set by declaring your own bean, not by a key per driver knob.

### `stx.mongo`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Opens an `stx-mongo` coroutine client |
| `uri` | string | — | Required when enabled |
| `database` | string | — | Required when enabled |

Not `stx.data.mongo`, and not Spring Boot's `spring.mongodb`. Those configure Spring Data's
`ReactiveMongoTemplate`; this hands you `stx-mongo`'s coroutine client. Different APIs onto the same
server — turning both on means two connection pools, which should be a decision somebody made.

### `stx.jpa`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Builds a Hibernate Reactive session factory at startup |
| `uri` | string | `"postgresql://localhost:5432/postgres"` | **Not a JDBC URL** — a `jdbc:` prefix is the first thing to suspect when a connection fails for no visible reason |
| `username` | string | — | Sent separately, because the URI does not carry it and must not be made to |
| `password` | string | — | The same |
| `schema` | string | — | Left to the server's `search_path` when unset |
| `packages` | list | `[]` | Required when enabled. The scan also finds `@Converter`s, which naming classes one by one does not |
| `naming` | enum | `snake-case` | `created_by`, not `createdby` |
| `schema-mode` | enum | `none` | `none` outside tests: a schema is migrated by something that keeps a history |
| `pool-size` | integer | `10` | A reactive pool is not sized like a blocking one — a connection is held for a statement, not a request |
| `connect-timeout` | duration | — | Worth setting. A request queued behind an exhausted pool has already lost |
| `idle-timeout` | duration | — | |
| `statement-cache-size` | integer | — | Off in the driver by default, and the cheapest performance setting here |
| `batch-size` | integer | — | Unset is one statement per row, which is what makes a bulk load slow |
| `show-sql` | boolean | `false` | Useful once, expensive always |
| `properties` | map | `{}` | Anything else Hibernate understands, applied last and overriding everything above |

This one blocks the thread that is starting the application: `Jpa.connect` suspends and a `@Bean`
method cannot. On the default `schema-mode` nothing connects at startup either way — the pool opens
its first connection when something asks for a session, so a wrong password surfaces on first use.
Any other mode has schema work to do and connects.

### `stx.storage`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Opens an `stx-storage` connection |
| `endpoint` | string | — | Required when enabled |
| `access-key` | string | — | Required, and with no default on purpose |
| `secret-key` | string | — | The same |
| `region` | string | — | Can stay unset against MinIO. Against S3 the SDK will otherwise ask where each bucket lives |

No buckets are created. `ensureBucket` is one call and belongs to whoever knows which buckets the
application needs; creating them from a property list would make startup write to somebody's object
store out of a config file nobody reviewed as a schema.

### `stx.redis`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Opens an `stx-redis` connection |
| `uri` | string | `"redis://localhost:6379"` | |
| `namespace` | string | `""` | Prefixed to every key. Worth setting whenever the server is shared — Redis has one flat keyspace per database |
| `timeout` | duration | — | How long a command waits before failing |

### `stx.kafka`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Registers the cluster handle |
| `bootstrap` | string | `"localhost:9092"` | `host:port` pairs, comma-separated |
| `client-id` | string | — | Worth setting: it is what a broker's metrics and logs name, and `consumer-1` in an incident is not a name |
| `properties` | map | `{}` | |

**This one opens nothing and has no `close()`.** A Kafka client connects when it is constructed, so
the connections belong to the publishers, subscribers and admin clients the handle gives out — each
with its own lifetime and each closed by whoever asked for it. It is the one integration where the
application still owns real resources.

### `stx.amqp`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Opens an `stx-amqp` connection at startup |
| `uri` | string | `"amqp://localhost:5672"` | See the vhost note below |
| `connection-name` | string | — | What the connection calls itself in the broker's management UI |
| `heartbeat` | duration | — | What makes a dead connection look dead |
| `connection-timeout` | duration | — | |
| `recovery` | boolean | `true` | Reconnect and re-declare after a broker restart |

**The vhost is the part of the URI most often got wrong.** AMQP's default vhost is *named* `/`, while
a URI path of `/` means the **empty** vhost — so `amqp://localhost:5672/` authenticates against a
vhost most brokers do not have and fails with an error about permissions rather than about spelling.

```
amqp://localhost:5672          the default vhost — no path at all
amqp://localhost:5672/%2F      the same thing, spelled out
amqp://localhost:5672/billing  a vhost named billing
amqp://localhost:5672/         the empty vhost, which is probably not what was meant
```

Without a `heartbeat`, a broker restart or a dropped NAT mapping leaves a socket that reads as open
and delivers nothing, and the client sits there.

Unlike `stx.jpa`, this one really does open a socket at startup: a service whose work arrives over
that connection should fail its boot when the broker is not there, rather than start and quietly
consume nothing.

---

### `stx.workflow`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Builds an `stx-workflow` engine over the `WorkflowStore` bean |
| `store` | `redis` \| `jpa` \| `mongo` | — | Which store to build. Unset means the application declares its own |
| `lease` | duration | `30s` | How long after a process dies before somebody else may pick up what it was doing |
| `retention` | duration | `7d` | How long a finished instance is kept before it is expired |
| `child-poll` | duration | `1m` | How often a parent parked on a `child` node looks at the child again |

**Every `Workflow<*>` bean is registered with it.** An instance is stored under its workflow's *name*
and an engine that cannot look that name up cannot resume it after a restart — so a workflow is
registered by existing as a bean, rather than by also being remembered in a list somewhere.

There is no `uri`. Each `store` value builds over the connection the matching `stx.*` group already
opened — the `Redis`, the `Jpa` or the `MongoDatabase` bean — rather than a second pool for the same
server. **Nothing is inferred**: an application with both a `Redis` and a `Jpa` bean is not saying
where its workflow instances belong, and a library that guessed would put them somewhere plausible
and wrong. Leave `store` unset and declare a `WorkflowStore` bean, and `@ConditionalOnMissingBean`
steps aside for it.

`store: jpa` needs `com.softistx.workflow.jpa` in `stx.jpa.packages`, or the session factory has no
`WorkflowInstanceRow` to map.

`lease` is **not** a deadline on a step — the lock renews while the work runs. A `Failed` instance is
exempt from `retention` whatever it says: it is waiting for a person, and expiring it would delete
the only description of what needs fixing. `store: jpa` ignores `retention` — a table has no TTL, so
retention there is `JpaWorkflowStore.purge` on a schedule the application owns. Both are ignored when
the application declares its own store.

`child-poll` is a safety net rather than the mechanism: a child workflow resumes its parent the
moment it finishes, and the poll only covers a process that died between those two writes. It needs
`stx.workflow.worker.enabled`, or an application scheduler calling `resume` — nothing here polls on
its own. Unlike `lease` and `retention` it belongs to the engine, so it applies whichever store the
application ends up with.

### `stx.workflow.worker`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Runs a `WorkflowWorker` in this process |
| `poll` | duration | `1s` | How long it waits between two looks at an empty index |
| `batch` | integer | `32` | How many due instances it takes at a time |
| `concurrency` | integer | `8` | How many it advances at once |

**Off by default, and that is a decision rather than caution.** Enabling `stx.workflow` gives an
application a way to *run* workflows; enlisting it in recovering every abandoned instance in the
fleet is a separate question, and one whose answer usually differs between the API pods and the two
boxes that are supposed to do the recovering.

The worker is a `SmartLifecycle`, so it starts after the context is refreshed and stops before it is
torn down, on a scope of its own. A worker started in an `@PostConstruct` would resume instances
against half-built collaborators; one that outlived the context would resume them against closing
ones.

### `stx.migrations`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Runs the migrations before the context finishes refreshing, and refuses to start if any fails |
| `store` | `mongo` \| `sql` | — | Which ledger to build. Unset means the application declares its own `MigrationRunner` bean |
| `name` | string | `stx_migrations` | The table or collection the ledger lives in; the lock is `<name>_lock` |
| `lease` | duration | `5m` | How long the migration lock is good for before the process holding it is assumed gone |
| `lock-timeout` | duration | `5m` | How long to wait for another instance to finish migrating before failing to start |
| `lock-poll` | duration | `1s` | How long to wait between two attempts on the lock |
| `stale-after` | duration | `15m` | How old a `RUNNING` record has to be before it is read as a process that died |

**A migration is a `@Component` implementing `MongoMigration` or `SqlMigration`,** collected by type.
By type rather than by annotation, which is a correction the runner this replaces already carried: the
version before it filtered on an annotation and silently ignored anything without it — a migration
that does not happen and does not say so.

```kotlin
@Component
class V1Orders : SqlMigration {
    override val version = 1L
    override suspend fun migrate(context: SqlMigrationSession) {
        context.execute("create table if not exists orders (id bigint primary key)")
    }
}
```

There is no `uri`. Each `store` value builds over the connection the matching `stx.*` group already
opened — the `Jpa` bean, or the `MongoDatabase` bean, which `stx.data.mongo` will also bridge from
Spring Data's own factory. **Nothing is inferred**: an application with both beans is not saying which
schema it means to migrate. An application migrating both names one here and declares a
`MigrationRunner` bean for the other — the gate runs every runner it finds.

Naming a `store` whose connection bean does not exist **fails at startup**. A context that came up
quietly with no migrations, against a schema nobody made, is the failure this library exists to
prevent.

The gate is an `InitializingBean`, and that is the whole design: `SuspendingListenerTest` pins that
Spring does not wait for a suspending listener, so the `@EventListener(ApplicationReadyEvent)` this
replaces let the port open while migrations were still running. A throw out of `afterPropertiesSet`
aborts the refresh instead — no web server, no `ApplicationReadyEvent`, no requests. The vocabulary is
[`docs/migrations.md`](migrations.md).

`store: mongo` on an application using **Spring Data** needs no `stx.mongo`: `stx-spring-boot`
contributes a coroutine `MongoDatabase` built from the `ReactiveMongoDatabaseFactory` already in the
context. Turning on `stx.mongo` for it would open a second pool against the same server, and under
test one that never saw the harness's per-run database suffix.

## `stx-telemetry-spring`

Logs and traces. The vocabulary is [`docs/telemetry.md`](telemetry.md); the reasoning is
[the library's README](../libs/stx-telemetry/stx-telemetry/README.md);
[`examples/spring-orders`](../examples/spring-orders/README.md) is these keys running — the yaml, a
`span { }` in the service, typed events in `service/OrderEvents.kt`, and a spec that asserts on the
signals rather than on the configuration.

### `stx.telemetry`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Builds an stx-telemetry root, installs it so `logger<T>()` and `span { }` find it anywhere, and makes each request a server span. Every `Exporter` bean is added to it. An application's own `Telemetry` bean wins |
| `service` | string | *spring.application.name* | The `service.name` every signal reports, falling back to `unknown-service` when there is neither. Worth setting: it is the attribute every backend groups by |
| `version` | string | *empty* | `service.version` |
| `environment` | string | *empty* | `deployment.environment.name` — `production`, `staging` |
| `minimum` | `debug` \| `info` \| `warn` \| `error` | `info` | The lowest severity emitted at all. Spans are governed by [sample-ratio], not by this |
| `sample-ratio` | double | `1.0` | The share of traces kept. Decided once at a trace's root, carried in the `traceparent`, and computed from the trace id rather than a coin toss — so two services at the same ratio keep the *same* traces. Logs are never sampled |
| `stack-traces` | boolean | `true` | Whether a failure's stack trace is rendered into the signal |
| `batch` | int | `512` | How many signals ship together at most |
| `linger` | duration | `1s` | How long a partial batch waits for company |
| `drain-timeout` | duration | `10s` | How long shutdown waits for the queue, so a collector that stopped answering does not stop the process exiting |
| `console` | boolean | `false` | Adds a `ConsoleExporter` — one readable line per signal on stdout. For development |
| `json-lines` | boolean | `false` | Adds a `JsonLinesExporter` — one JSON object per line, for a collector that reads the container's log |
| `web-filter` | boolean | `true` | Whether each request becomes a server span. A `CoWebFilter`, so a suspending `@RestController` method is inside the span — which is what a `WebFilter` returning a `Mono` could not give it |
| `ignore` | list | *empty* | Path prefixes that get no span. A health check answered every second by a load balancer is a trace nobody will read and most of the traces there are |

### `stx.telemetry.otlp`

Needs `stx-telemetry-otlp` on the classpath; the beans below are absent without it.

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Exports to an OTLP collector over HTTP in JSON, with no OpenTelemetry SDK |
| `endpoint` | string | `http://localhost:4318` | The collector's base URL; `/v1/logs` and `/v1/traces` are appended |
| `headers` | map | *empty* | Sent on every request — an API key, a tenant header |
| `timeout` | duration | `10s` | Connect and request timeout |
| `attempts` | int | `3` | How many times one document is sent. A 408, 429 or 5xx and a connection failure are retried; any other 4xx is not, because the same document would be wrong again. A `partialSuccess` is never retried — the accepted records would arrive twice |
| `backoff` | duration | `500ms` | The first wait between attempts; it doubles each time |
| `gzip` | boolean | `true` | Compresses the body. Every OTLP/HTTP receiver is required to understand it |

### `stx.telemetry.slf4j`

Needs `stx-telemetry-slf4j` on the classpath; the beans below are absent without it.

The bridge pointed **outward** — `stx-telemetry`'s signals written to the application's own logging,
for a deployment that already has logback and an appender fleet it trusts. The same module's
`SLF4JServiceProvider` points it the other way, so that Hibernate, Lettuce and Kafka land in this
pipeline; that direction has no key here because it is decided by the classpath, not by a property.
**Both at once is a loop**, and the exporter refuses to be built when it finds the provider bound —
so this key and that provider together fail the context at startup rather than later.

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Adds an `Slf4jExporter`, which writes each signal to the bound SLF4J with `traceId` and `spanId` in the MDC — so a `%X{traceId}` pattern prints them. An `ILoggerFactory` bean, if the application has one, is where the lines go |
| `spans` | boolean | `true` | Whether completed spans are logged too, one line each, under `com.softistx.telemetry.span`. False for an application that wants the logs here and reads its traces somewhere else |
| `span-severity` | `debug` \| `info` \| `warn` \| `error` | `info` | The level a span that succeeded is logged at. One that failed is always `error` |

### `stx.telemetry.file`

A rotating file on the local disk, in the format `stx.telemetry.json-lines` writes to stdout — for a
deployment with no collector, or a container whose stdout is already crowded with somebody else's
output. No extra module: `FileExporter` is in `stx-telemetry` itself.

`max-size` and `every` are both on and answer different questions — one bounds the disk, the other
bounds how old the newest closed file is. A service that logs a little would keep yesterday in the
open file under a size limit alone; one that logs a lot would fill the disk before midnight under a
period alone. `0` turns either off.

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Adds a `FileExporter`. The file is opened when the context starts, so a path that cannot be written fails there rather than on the export path |
| `path` | string | `logs/telemetry.jsonl` | The active file. Parent directories are created if missing |
| `max-size` | data size | `64MB` | The size at which the file is rolled aside. `0` for no size limit |
| `every` | duration | `24h` | The period one file covers, **aligned to the epoch** — `24h` rolls at UTC midnight and `1h` at the top of the hour, not a day or an hour after this process started. `0` for no time limit |
| `keep` | int | `7` | How many rolled files survive. `0` keeps only the file being written |
| `compress` | boolean | `false` | Whether a rolled file is gzipped. Off by default: it happens on the pipeline's export path, and a 64 MB file is about a second of the consumer not draining |

### `stx.telemetry.mongo`

Needs `stx-telemetry-mongo` on the classpath; the bean below is absent without it.

One document per signal, with the field names the JSON-lines format uses, so a query against the
collection reads like a `jq` filter against a file. **Retention is a TTL index**, not a job: Mongo
expires the documents itself, on the primary, whether or not this process is running.

The client is **telemetry's own**, built from `uri` and closed with the context — not the
application's. A burst of telemetry on the pool business requests are queueing for turns an
observability problem into an outage, and this also works in an application that has no Mongo at all.

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Adds a `MongoExporter`. A batch is one unordered `insertMany`, so a document the server rejects costs its own record and not the five hundred behind it |
| `uri` | string | `mongodb://localhost:27017` | The connection string for telemetry's own client |
| `database` | string | `telemetry` | The database the collection lives in |
| `collection` | string | `telemetry` | The collection documents are inserted into |
| `retention` | duration | `30d` | How long a signal is kept, as a TTL index on `at`. Changing it rebuilds the index rather than leaving the old window in place. `0` keeps everything |

---

## `stx-graphix-spring`

Not this module — `com.softistx:stx-graphix-spring`. The keys follow the same opt-in rule, and the
metadata lives in that module's `additional-spring-configuration-metadata.json`.

### `stx.graphix`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Serves POST/GET GraphQL at [path]. Collects `@GraphQLController` beans as roots, plus `GraphQLScalarType`, `GraphixDirective`, `GraphixCustomizer`, `GraphixInterceptor`, `GraphQLEngineCustomizer` and `GraphixExceptionHandler` beans. Those beans are ordinary Spring objects, so a mutation's `OrderService` is constructor injection, not GraphQL context. An application's own `Graphix` bean wins |
| `path` | string | `/graphql` | HTTP path |
| `subscriptions` | `sse` \| `graphql-ws` | `sse` | `sse` is `text/event-stream` on POST. `graphql-ws` is a WebSocket on [path] (`graphql-transport-ws`); HTTP POST of a subscription is then 400 |
| `schema-locations` | list | `classpath:graphql/` | Directories of `.graphqls` / `.gqls` files, scanned recursively and merged. Empty scan keeps the annotated schema. Same default as Spring GraphQL |
| `schema-file-extensions` | list | `.graphqls,.gqls` | File suffixes under [schema-locations] |
| `introspection` | boolean | `true` | Whether `__schema` and `__type` answer. Off, a document selecting either comes back as a GraphQL error; `__typename` and every other field are unaffected, and the schema itself is unchanged. Ignored when the application supplies its own `Graphix` bean |
| `built-in-scalars` | boolean | `true` | Whether every built-in scalar is in the schema — `LocalDate`, `BigDecimal`, `PositiveInt` and the rest — whether or not a field uses one. Off, the schema carries only the ones a field resolved to, and a bounded scalar is reached by declaring it in SDL. Ignored when the application supplies its own `Graphix` bean |
| `sandbox` | boolean | `false` | Serves an Apollo Sandbox page at [sandbox-path]. Off by default: enabling GraphQL must not also open an HTML page that advertises the schema |
| `sandbox-path` | string | `/sandbox` | Where that page is served — a sibling of [path], not a child |
| `sandbox-endpoint` | string | *empty* | GraphQL URL the sandbox opens with. Empty resolves it in the browser from the page's own origin and [path], which is what survives a proxy, https and a republished port |

A field error is HTTP 200 plus `errors[]`. Malformed JSON is HTTP 400. The annotation vocabulary
is [`docs/graphix.md`](graphix.md).

---

## Where these keys come from

The IDE completes them from
`libs/stx-spring-boot/resources/META-INF/additional-spring-configuration-metadata.json`, which is
written by hand.

It has to be. `spring-boot-configuration-processor` is a *Java* annotation processor, this toolchain
has no kapt, and its `settings.java.annotationProcessing` runs javac over Java sources only — so the
processor never sees a Kotlin `@ConfigurationProperties` class.

`ConfigurationMetadataTest` is what keeps that file honest: it scans the module for
`@ConfigurationProperties` classes and fails on a documented key the code no longer declares, on a
declared key nobody documented, and on an entry missing its type, description, source or default.
Without it the file would rot the first time somebody renamed a property, and the only symptom would
be autocompletion quietly missing an entry, which nobody reports.

**A new key is added to that file in the same change that reads it** — and to the table above, which
is the part a reader finds.
