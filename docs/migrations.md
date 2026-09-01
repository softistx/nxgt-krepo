# What a stx-migrations migration may say

The vocabulary of [`stx-migrations`](../libs/stx-migrations/stx-migrations/README.md): what a migration
is, what the runner does with it, and what each store's ledger holds. The module README explains *why*
the library is shaped this way; this file is what you may write.

- [Writing a migration](#writing-a-migration)
- [The version](#the-version)
- [Statuses and their transitions](#statuses-and-their-transitions)
- [What the runner does, in order](#what-the-runner-does-in-order)
- [What it throws](#what-it-throws)
- [The ledger contract](#the-ledger-contract)
- [The two stores](#the-two-stores)
- [The lock](#the-lock)
- [What a killed process leaves](#what-a-killed-process-leaves)
- [In a Ktor application](#in-a-ktor-application)
- [In a Spring Boot application](#in-a-spring-boot-application)
- [A migration must be safe to attempt twice](#a-migration-must-be-safe-to-attempt-twice)
- [Deliberately absent](#deliberately-absent)

## Writing a migration

A migration is a class. It has a version, an optional description, and a `migrate` that suspends:

```kotlin
interface Migration<in C> {
    val version: Long
    val description: String get() = this::class.java.simpleName
    suspend fun migrate(context: C)
}
```

`C` is what the store hands it. You never implement `Migration` directly — each store has an empty
marker sub-interface, and that is what makes the type distinguishable at runtime:

```kotlin
interface MongoMigration : Migration<MongoDatabase>
interface SqlMigration : Migration<SqlMigrationSession>
```

One line each, and they earn it. A generic `Migration<*>` erases: a Spring `ObjectProvider` or any
scan could not tell a Mongo migration from a SQL one, so an application with both stores would hand
every migration to both runners.

## The version

```kotlin
override val version = 1L
```

Declared, never parsed from the class name. Sequential integers and timestamps
(`20260901120000` — which is why it is a `Long`) are both fine; what matters is that **the number
never changes once it has been applied anywhere**, because the ledger is keyed on it.

Two migrations at one version are refused when the runner is **built** — a Spring context that will
not refresh, a Ktor server that never binds — rather than at the moment one of them would have run.

`description` defaults to the class's own simple name and is recorded beside the version. It is for
whoever reads the ledger later; nothing keys on it.

## Statuses and their transitions

```
              claim                 migrate returns
   (absent) ─────────► RUNNING ──────────────────► APPLIED
                          │
                          │ migrate throws
                          ▼
                        FAILED
```

| | |
| --- | --- |
| `RUNNING` | claimed and being applied — or claimed by a process that died while applying it |
| `APPLIED` | done. Skipped forever after, and never re-run |
| `FAILED` | `migrate` threw. Nothing after it ran, and nothing will until a person has looked |

**There is no `PENDING`.** A migration the ledger has never heard of is pending; that is the absence of
a record, and writing a row to say so would be a write before the lock is held.

Nothing moves *out* of `APPLIED` or `FAILED` on its own. Clearing a `FAILED` record is an operator
action — see [What a killed process leaves](#what-a-killed-process-leaves).

## What the runner does, in order

```kotlin
class MigrationRunner<C>(
    ledger: MigrationLedger,
    migrations: List<Migration<C>>,
    context: MigrationContext<C>,
    lockTimeout: Duration = 5.minutes,
    lockPoll: Duration = 1.seconds,
    staleAfter: Duration = 15.minutes,
    identity: String = migrationIdentity(),
)

suspend fun run(): List<MigrationRecord>
```

1. Sort by version and refuse duplicates — **in the constructor**, before anything is touched.
2. `ledger.prepare()`. Idempotent, and called by the runner so nobody can forget it.
3. Nothing to run → answer with `ledger.all()`, taking no lock.
4. Take the lock, waiting up to `lockTimeout` and re-asking every `lockPoll`.
5. Inside the lock: `ledger.blocking(staleAfter)` → refuse if anything is `FAILED`, or `RUNNING` and
   older than `staleAfter`.
6. Per migration, in version order: `APPLIED` is skipped; otherwise claim it as `RUNNING`, apply it,
   and mark it `APPLIED` with how long it took — or `FAILED`, and stop.
7. Answer with `ledger.all()` — the whole ledger, not only what this run wrote, because *nothing to
   do* is an answer worth being able to see.

`identity` is `host/pid/abcd1234` by default. The host and the pid are what an operator looking at a
stuck `RUNNING` record needs; the random suffix keeps two runners inside one JVM distinct, which is
what every spec that asks whether a second process is turned away depends on.

## What it throws

Every one of these leaves `run()`, and that is what makes the runner a gate.

| | when |
| --- | --- |
| `DuplicateMigrationVersionException` | two migrations claim one version. Thrown when the runner is **built** |
| `MigrationHaltedException` | the ledger holds a `FAILED` record, or a `RUNNING` one older than `staleAfter` |
| `MigrationFailedException` | `migrate` threw. The record says `FAILED`; the cause is the original exception |
| `MigrationLockTimeoutException` | somebody held the lock for longer than `lockTimeout` |
| `MigrationConflictException` | a version was claimed by somebody else *while this run held the lock* — which means the lock did not hold |

All five are `MigrationException`, which is `sealed`.

A cancellation is not one of them: if the coroutine running a migration is cancelled, the
`CancellationException` is rethrown untouched and the record stays `RUNNING`. That is the truth —
whether the change landed is a question only the database can answer, and writing `FAILED` would claim
we know it did not.

## The ledger contract

```kotlin
interface MigrationLedger {
    suspend fun prepare()
    suspend fun find(version: Long): MigrationRecord?
    suspend fun claim(record: MigrationRecord): Boolean
    suspend fun update(record: MigrationRecord)
    suspend fun blocking(staleAfter: Duration): MigrationRecord?
    suspend fun all(): List<MigrationRecord>
    suspend fun <T> guarded(block: suspend () -> T): T?
}
```

Seven members, and every one of them is something a store does differently. What each promises:

- **`prepare`** creates the table, the index and the lock row, and does nothing when they are there.
  Called on every run, so it must be idempotent in the store's own terms — `create table if not
  exists`, an index creation that accepts an existing index *of the same name*, an insert whose
  duplicate-key error is swallowed.
- **`claim`** is an insert, never an upsert and never a save. `false` means somebody else got there
  and the caller must not overwrite what they wrote.
- **`update`** overwrites the record `claim` already put there.
- **`blocking`** answers the **lowest** version that is `FAILED`, or `RUNNING` with `updatedAt` older
  than `staleAfter`. Only ever called while the lock is held.
- **`all`** is lowest version first.
- **`guarded`** declines rather than queues, and `null` means *somebody else is migrating*. There is no
  `id` parameter: one ledger, one lock.

Nothing is `AutoCloseable` — a ledger takes a connection it did not open and does not close it.

`MigrationRecord` carries `version`, `description`, `status`, `at` (when the version was claimed, and
it never moves), `updatedAt` (every write), `appliedBy`, `failure` and `durationMillis`.

`InMemoryLedger` in the core module is the reference implementation. It is a fair choice for a
single-process tool or a test double; anything with a second instance of the application wants a store.

## The two stores

Both live in `stx-migrations-db`, one package each, and both take a connection they did not open.

```kotlin
MongoMigrations(database, listOf(V1Seed(), V2Tags()))            // MongoDatabase
SqlMigrations(jpa, listOf(V1Orders(), V2OrderIndex()))           // Jpa — PostgreSQL or MySQL
```

| | MongoDB | SQL |
| --- | --- | --- |
| the records | collection `stx_migrations`, `_id` is the version | table `stx_migrations`, `version bigint primary key` |
| the lock | collection `stx_migrations_lock`, one document | table `stx_migrations_lock`, one row |
| uniqueness | the `_id`, so there is **no secondary index** | the primary key |
| the instants | BSON dates | `bigint` epoch milliseconds |
| what a migration gets | the `MongoDatabase` itself | a `SqlMigrationSession` over a borrowed connection |
| needs | no replica set — nothing opens a transaction | `poolSize >= 2`, refused at construction otherwise |

**Why the instants differ.** Mongo has one unambiguous date type and the two SQL servers do not:
MySQL's `timestamp` converts to UTC on the way in and back to the session's zone on the way out,
`datetime` does not, and Postgres has no `datetime` at all. A `bigint` means one thing everywhere —
`to_timestamp(started_at / 1000)` is the reading glasses.

**Why SQL wants two connections.** A run holds one for the migration's own statements and needs a
second for the lease watchdog renewing the lock underneath it. On a pool of one that is not an error,
it is a hang, so `SqlMigrations` refuses to be built and names the deadlock.

### SQL migrations write their own SQL

```kotlin
interface SqlMigrationSession {
    suspend fun execute(@Language("SQL") sql: String)   // sent as written; DDL lives here
    suspend fun update(@Language("SQL") sql: String): Int   // prepared; answers a row count
}
```

`execute` goes out unprepared, which is what makes `create index`, two statements in one call and a
literal `?` used as a Postgres `jsonb` operator reach the server unchanged. `stx-jpa`'s `NativeDdlTest`
measures each of those, and measures the session's own native verbs refusing them.

**Neither verb takes parameters.** Postgres spells a placeholder `$1` and MySQL spells it `?`, so a
parameter list here would be a portability hole in the one API that is supposed to be portable. A
migration is authored code: the values it needs are literals it writes itself, exactly as in a `.sql`
migration file. **There is no `select`** either — a portable row type would have to be positional, and
a read-modify-write loop inside a startup gate is the thing to avoid.

**An unqualified name lands in the connection's own schema**, not in `JpaConfig.schema`: Hibernate
applies that when it renders a statement from the mapping, and nothing renders these. The ledger
qualifies its own two tables; a migration should say where its tables go.

The whole dialect surface is that one placeholder. It used to be two things — the second was *insert
unless it is already there* — and the contract took it away: MySQL's Vert.x client sets
`CLIENT_FOUND_ROWS`, so an insert that hit an existing row reports one affected row exactly as a
successful insert does, and the question cannot be answered from a row count at all. `claim` inserts
plainly and, if the insert fails, asks the database whether the row is now there.

**PostgreSQL and MySQL are verified; DB2 is out of scope** and refused by name at construction.

## The lock

One lock per ledger, built out of two fields — an owner and an expiry — that one conditional write
sets, another extends and a third clears. The policy around those three writes is `Lease` from
`stx-common`: it **declines rather than queues**, **renews at a third of its duration** while the work
runs, and **releases under `NonCancellable`**.

The runner is what turns a refusal into a wait. That is the opposite of `WorkflowStore.guarded`'s
caller, and deliberately: a fleet of workers declining a busy instance goes off and does other work,
whereas a fleet of application instances declining the migrations would go off and start *serving*,
against a schema that does not exist yet.

`staleAfter` is only consulted while this run holds the lock, which is what makes "stale" unambiguous —
a process that were still alive would still be holding the lock.

## What a killed process leaves

The next startup **refuses in every case**, naming the version and the host that claimed it. What
differs is the state of the data underneath.

| store | the lock | the data | clearing it |
| --- | --- | --- | --- |
| MongoDB | expires at most one lease later | whatever the migration wrote is written | delete or fix the `RUNNING` document |
| PostgreSQL | same | DDL is transactional, so a migration that ran its statements in one transaction left nothing | one `update` on the ledger row |
| MySQL | same | DDL is **not** transactional, so a multi-statement migration may be half applied | check the schema, then one `update` |

This is why a migration has to be re-attemptable: the cheapest repair is to make it safe to run again
and clear the record.

## In a Ktor application

```kotlin
install(JpaConnection) { config = JpaConfig(uri = …, username = …, password = …) }
install(Migrations) {
    gate(SqlMigrations(application.jpa, listOf(V1Orders(), V2OrderIndex())))
    injectable = true          // optional: the ledger through Ktor's DI
}

get("/health/migrations") { call.respond(call.migrations.map { "${it.version} ${it.status}" }) }
```

`install(Migrations)` **goes after the connection plugin it reads from** — the runner is built from
what `install(JpaConnection)` or `install(MongoConnection)` put on the application, and Ktor runs
install blocks in order. More than one `gate(…)` is allowed and they run in the order added; they are
separate ledgers with separate locks, so *in order* is a statement about this process rather than a
transaction across two servers.

The plugin blocks inside `install`, which is what makes it a gate: an exception leaves the install
block, leaves `embeddedServer`, and the port is never opened. `call.migrations` is the ledger as it
stood at startup — a snapshot, because once the gate has passed only another process can change it.

**The list of migrations is explicit and there is no scan.** `stx-jpa`'s `scanEntities` states the
position: *"a list breaks the build when a class moves; a scan finds nothing and starts perfectly, and
the first query is where you learn about it."* For migrations, that second failure is what the library
exists to prevent.

## In a Spring Boot application

```yaml
stx:
  jpa: { enabled: true, uri: postgresql://localhost:5432/orders, username: …, password: … }
  migrations: { enabled: true, store: sql }
```

```kotlin
@Component
class V1Orders : SqlMigration {
    override val version = 1L
    override suspend fun migrate(context: SqlMigrationSession) {
        context.execute("create table if not exists orders (id bigint primary key)")
    }
}
```

**A migration is a bean, collected by type.** By type rather than by annotation, which is a correction
the runner this replaces already carried: the version before it filtered on an annotation and silently
ignored anything without it — a migration that does not happen and does not say so.

The gate is an `InitializingBean`, and that is the design rather than a detail. `SuspendingListenerTest`
pins that Spring does not wait for a suspending listener, so the `@EventListener(ApplicationReadyEvent)`
this replaces let the port open while migrations were still running. A throw out of
`afterPropertiesSet` aborts the refresh: no web server, no `ApplicationReadyEvent`, no requests.

`stx.migrations.store` names one ledger and nothing is inferred; an application migrating both stores
names one and declares a `MigrationRunner` bean for the other, because the gate runs every runner it
finds. Naming a store whose connection bean does not exist **fails at startup**. Every key is in
[`docs/spring-configuration.md`](spring-configuration.md).

## A migration must be safe to attempt twice

Not because the runner will — an `APPLIED` record is skipped — but because the row above says an
operator may have to make it. In practice:

```kotlin
context.execute("create table if not exists orders (…)")
context.execute("alter table orders add column if not exists currency varchar(3)")

database.getCollection<Document>("orders").updateMany(exists("tags", false), set("tags", emptyList()))
```

Each of those is the same statement written so that running it a second time is a no-op rather than an
error.

## Deliberately absent

- **`rollback`.** A declared-but-uncalled one *"reads as a promise that a failed migration is undone,
  and no code anywhere kept that promise"* — the prior art's own words. A change that has to be undone
  is undone by the next migration, which the ledger records and a person reviewed.
- **Checksums.** Flyway hashes a file because a file can be edited in place after it ran. A migration
  here is a class in the repository: the diff is the review, and a hash could only tell you later, and
  from a database, what a `git blame` already tells you.
- **`.sql` files and resource scanning.** A migration is Kotlin everywhere, with SQL as
  `@Language("SQL")` strings the IDE injects into. A list of classes breaks the build when one moves;
  a scan finds nothing and starts perfectly.
- **Contexts, labels, baselines and a `repair` verb.** Each of them is a way to make the ledger
  disagree with the code, and the failure this library exists to prevent is exactly that disagreement
  going unnoticed.
- **The ledger write inside the migration's own transaction.** It is the best guarantee available — on
  PostgreSQL a killed process would leave no trace at all — and it needs the ledger and the context to
  share a connection, which these two interfaces deliberately do not express. It is the known next
  step, not an oversight.
