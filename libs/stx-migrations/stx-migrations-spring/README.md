# stx-migrations-spring

Spring Boot auto-configuration for `stx-migrations`, opt-in behind `stx.migrations.enabled`.

**`io.github.softistx:stx-migrations-spring`** — [how to depend on it](../../../docs/consuming.md).

```yaml
stx:
  jpa: { enabled: true, uri: postgresql://localhost:5432/orders, username: …, password: … }
  migrations: { enabled: true, store: sql }
```

```kotlin
@Component
class V1Orders : SqlMigration {
    override val version = 1L
    override val description = "the orders table"

    override suspend fun migrate(context: SqlMigrationSession) {
        context.execute("create table if not exists orders (id bigint primary key, total numeric(12, 2))")
    }
}
```

Every key is in [`docs/spring-configuration.md`](../../../docs/spring-configuration.md); the
vocabulary is [`docs/migrations.md`](../../../docs/migrations.md).

## `InitializingBean`, not a suspending listener

This is the reason the module exists rather than being two lines of application code.

`SuspendingListenerTest` in `stx-spring-boot` pins that **Spring does not wait for a suspending
listener**: `publishEvent` returns while the listener is still suspended. The migration runner this
library replaces was a suspending `@EventListener(ApplicationReadyEvent)`, so the port opened while
migrations were still running — and `examples/spring-orders` worked around it by polling the ledger
from its own specs before asserting anything.

`MigrationGate.afterPropertiesSet` runs during the refresh, and a throw out of it aborts the refresh:
no web server, no `ApplicationReadyEvent`, no requests. `MigrationGateTest` asserts exactly that, and
it is the spec that must not be skipped.

`runBlocking` inside it, for the reason every bootstrap in these libraries uses it: `run()` suspends
and a lifecycle callback cannot. It happens once, on the thread already blocked waiting for the
context.

## Migrations are collected by type

`ObjectProvider<SqlMigration>` and `ObjectProvider<MongoMigration>` — a migration is registered by
existing as a bean. By type rather than by annotation, which is a correction the prior art already
carried: the version before it *"filtered on the annotation and silently ignored anything without it,
which is a migration that does not happen and does not say so."*

The two marker interfaces are what make this possible at all. A generic `Migration<*>` erases, so an
`ObjectProvider` could not tell a Mongo migration from a SQL one and an application with both stores
would hand every migration to both runners.

## Nothing is inferred about where the ledger goes

`stx.migrations.store` is `mongo` or `sql`, and each builds over the connection the matching `stx.*`
group already opened — never a second pool for the same server. An application with both a `Jpa` and a
`MongoDatabase` bean is not saying which schema it means to migrate, and a library that guessed would
write a ledger somewhere plausible and wrong.

Leaving `store` unset means the application declares its own `MigrationRunner` bean, and the gate runs
every runner it finds — which is also how an application migrating **both** stores does it: name one
here, declare the other.

**Naming a `store` whose connection bean does not exist fails at startup.** That is deliberate: a
context that came up quietly with no migrations, against a schema nobody made, is the failure this
library exists to prevent. `@ConditionalOnBean` on the connection would have turned it into a shrug.

Turning `stx.migrations` on with no store and no runner bean is *not* an error, though — the gate is
empty. Enabling the feature before writing the first migration should not break a build.

## Shape

The two nested configurations are `@ConditionalOnClass` and nested for the reason
`stx-workflow-spring`'s are: a method signature naming a missing class is a `NoClassDefFoundError` at
refresh, and the condition on the outer class is evaluated too late to prevent it.
`stx-migrations-db`, `stx-jpa` and `stx-mongo` are all `compile-only` here, so an application that
names no store loads a class from none of them.

`@ConditionalOnProperty(havingValue = "true")` with **no `matchIfMissing`** — the repo's opt-in rule.
`enabled=false` and unset mean the same thing, and `MigrationGateTest` asserts both.

The configuration metadata under `resources/META-INF/` is **hand-written**, because Spring's metadata
processor is a Java annotation processor and never sees a Kotlin `@ConfigurationProperties` class.
`ConfigurationMetadataTest` checks the JSON against the classes, and `ConfigurationDocsTest` checks it
against `docs/spring-configuration.md` — the failure otherwise is silent, which is autocompletion
quietly missing a key and a reader quietly not finding it.

## Tests

No container. `InMemoryLedger` from the core is a real ledger with a real conditional claim and a real
lock, so what these specs exercise is the gate and the wiring: a failing migration aborting the
refresh, a second startup halting while the failure stands, two runners passing in order, an empty
gate, `enabled=false`, and a named store whose connection is missing failing by name.

That a ledger works against a real server is `stx-migrations-db`'s question, asked there against
MongoDB, PostgreSQL and MySQL. That the whole path works in a real application is
`examples/spring-orders`.

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
