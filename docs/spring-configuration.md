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
from the one `spring.data.mongodb`, `spring.data.redis`, `spring.kafka`, `spring.rabbitmq` and
`spring.datasource` configure. Setting both gives an application two clients, not one configured
twice — which is a legitimate thing to want and an expensive thing to do by accident.

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

These keys become a `com.strange.common.http.CorsPolicy`, which is also what `stx-ktor` installs
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

### `stx.data.mongo.migration`

| Key | Type | Default | |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Runs pending migrations after the application is ready |
| `prefix` | string | `"V"` | What a migration class name starts with. Matched literally — a `.` is a `.`, not a wildcard |
| `collection` | string | `"migrations"` | What makes a migration run once. Pointing it at an empty collection runs every migration again |

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

Not `stx.data.mongo`, and not Spring Boot's `spring.data.mongodb`. Those configure Spring Data's
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
