# What a stx-migrations migration may say

The vocabulary of [`stx-migrations`](../libs/data/stx-migrations/stx-migrations/README.md): what a migration
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
- [`prepare()` runs before the lock, and has to](#prepare-runs-before-the-lock-and-has-to)
- [What a killed process leaves](#what-a-killed-process-leaves)
  - [Clearing it](#clearing-it)
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

So a real one, on SQL:

```kotlin
class V1Orders : SqlMigration {
    override val version = 1L
    override val description = "the orders table"

    override suspend fun migrate(context: SqlMigrationSession) {
        context.execute(
            """
            create table if not exists orders (
                id         bigint primary key,
                reference  varchar(64) not null unique,
                status     varchar(16) not null,
                total      bigint not null,
                placed_at  timestamp not null
            )
            """.trimIndent(),
        )
    }
}
```

and the same thing on MongoDB, where a migration is handed the database itself:

```kotlin
class V2Tags : MongoMigration {
    override val version = 2L
    override val description = "backfill tags on orders written before the field existed"

    override suspend fun migrate(context: MongoDatabase) {
        context
            .getCollection<Document>("orders")
            .updateMany(Filters.exists("tags", false), Updates.set("tags", emptyList<String>()))
    }
}
```

**Raw `Document`, not the application's entity.** A migration writes what the database holds, not what
today's mapping says it should: the class may gain a field, lose one or be renamed tomorrow, and this
migration still has to mean in a year what it meant when it was reviewed. The same rule is why the SQL
one spells column names rather than going through `stx-jpa`. `examples/spring-orders` runs both of
these for real.

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

**The class name is free**, and that is the point of declaring the version rather than parsing it.
`V1Seed`, `SeedOrders` and `AddTagsToOrders` are the same migration to the ledger, so renaming one or
moving its package changes nothing about what has run. Prefixing with the version anyway — `V1Seed`,
`V2Tags` — is worth doing, because it makes the running order readable in a file tree; it is a
convention for people, and there is no property that turns it into a rule.

The default is truncated at `$$`: a Spring bean that also carries `@Transactional`, or that an aspect
matches, is a CGLIB subclass named `V1Seed$$SpringCGLIB$$0`, and that is not a description anybody
wants to find in a ledger. `ProxiedDescriptionTest` builds one and asserts what gets recorded.

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

```kotlin
class V3Currency(private val expectedOrders: Int) : SqlMigration {
    override val version = 3L
    override val description = "orders written before the column are in EUR"

    override suspend fun migrate(context: SqlMigrationSession) {
        // Two statements in one call. `execute` is unprepared, which is what allows it.
        context.execute(
            """
            alter table orders add column currency varchar(3);
            create index orders_currency on orders (currency);
            """.trimIndent(),
        )

        // `update` is prepared, and it answers with a row count. `execute` answers with nothing.
        val backfilled: Int = context.update("update orders set currency = 'EUR' where currency is null")
        require(backfilled <= expectedOrders) { "$backfilled orders is more than V3 meant to touch" }
    }
}
```

What a migration does with that count is its own business. Throwing on one it did not expect is the
useful case, and it behaves like any other failure: the record goes to `FAILED`, nothing after it
runs, and the application does not start.

Note what that example is *not*: re-attemptable. `add column` fails the second time, on both servers —
see [A migration must be safe to attempt twice](#a-migration-must-be-safe-to-attempt-twice) for what
to write instead, and why the library cannot fix this for you.

The library guarantees the **transport**, not the portability of the SQL you send through it. Which
server a migration is written for is the author's to know, exactly as it would be in a `.sql` file —
and `if not exists` is where the two verified servers part company. Measured on PostgreSQL 17.11 and
MySQL 8.4, the image `mysqlContainer()` pins:

| | PostgreSQL | MySQL |
| --- | --- | --- |
| `create table if not exists` | yes | yes |
| `create index if not exists` | yes | **no** — `ERROR 1064`, a syntax error |
| `alter table … add column if not exists` | yes | **no** — `ERROR 1064` |

MariaDB accepts all three; MySQL is the odd one out, and it fails at *parse* time, so there is no
partial application to reason about — the statement simply never runs.

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

## `prepare()` runs before the lock, and has to

The lock lives in a table the ledger creates, so the first thing every run does is create two tables
outside any lock of its own. On MongoDB that is one `insertOne` whose duplicate key is caught. On SQL
it is two `create table if not exists`, and **on PostgreSQL that clause is not atomic**: two sessions
issuing the same one at the same moment can both pass its existence check, and the loser fails with
`duplicate key value violates unique constraint "pg_type_pkey"` instead of doing nothing.

A rolling deploy starting several instances together is exactly that moment. `SqlMigrationLedger`
issues each statement twice if the first attempt fails — by the retry the winner has committed, the
existence check sees the table, and the statement does nothing. A second failure is a real one and
propagates: a bad `stx.jpa.schema`, a missing grant.

MySQL needs none of this — its `create table` takes a metadata lock — but the retry costs nothing
there, and a ledger that behaved differently per server is a ledger with a dialect in it.

## What a killed process leaves

The next startup **refuses in every case**, naming the version and the host that claimed it. What
differs is the state of the data underneath.

| store | the lock | the data |
| --- | --- | --- |
| MongoDB | expires at most one lease later | whatever the migration wrote is written |
| PostgreSQL | same | DDL is transactional, so a migration that ran its statements in one transaction left nothing |
| MySQL | same | DDL is **not** transactional, so a multi-statement migration may be half applied |

This is why a migration has to be re-attemptable: the cheapest repair is to make it safe to run again
and clear the record.

### Clearing it

A record looks like this — a row in `stx_migrations`, or a document keyed on the version:

```
 version | description      | status  | applied_by       | failure | duration_ms | started_at    | updated_at
       2 | backfill tags    | RUNNING | box-7/4182/9f3ac | <null>  |      <null> | 1756742400000 | 1756742400000
```

```javascript
{ _id: NumberLong(2), description: "backfill tags", status: "RUNNING",
  appliedBy: "box-7/4182/9f3ac", failure: null, durationMillis: null,
  startedAt: ISODate("2026-09-01T12:00:00Z"), updatedAt: ISODate("2026-09-01T12:00:00Z") }
```

**Deciding is the whole job; the statement is one line.** Look at the schema and answer one question:
did the change land?

*It did not land, or you have made the migration safe to run again* — **delete the record.** An
`update` will not do: `claim` is an insert, so a row that is there at all makes the next run refuse.

```sql
delete from stx_migrations where version = 2;
```
```javascript
db.stx_migrations.deleteOne({ _id: NumberLong(2) })
```

*It landed and only the record is wrong* — mark it applied, and the next run skips it.

```sql
update stx_migrations set status = 'APPLIED', failure = null where version = 2;
```
```javascript
db.stx_migrations.updateOne({ _id: NumberLong(2) }, { $set: { status: "APPLIED", failure: null } })
```

The lock releases itself one lease after the process died, so there is normally nothing to do about
it. To take it back sooner — the primary key is a literal `x` on SQL and the string `migrations` on
MongoDB, because a lock table with one row still needs one:

```sql
update stx_migrations_lock set locked_by = null, locked_until = null where id = 'x';
```
```javascript
db.stx_migrations_lock.updateOne({ _id: "migrations" }, { $set: { lockedBy: null, lockedUntil: null } })
```

Do that only once you are sure nobody is still migrating. The lease exists precisely so you do not
have to be sure.

## In a Ktor application

```kotlin
install(JpaConnection) { config = JpaConfig(uri = …, username = …, password = …) }
install(MongoDB) { config = MongoConfig(uri = …, database = "orders") }

install(Migrations) {
    sql(application.jpa) {
        migration(V1Orders(), V2OrderIndex())
    }
    mongo(application.database) {
        migration(V1Seed(), V2Tags())
    }
}

get("/health/migrations") { call.respond(call.migrations.map { "${it.version} ${it.status}" }) }
```

`sql { }` and `mongo { }` each build one ledger and add one runner. Both take the connection
explicitly — `application.jpa`, `application.database` — because that is the only thing about a Ktor
application the plugin could not otherwise know, and guessing it is how a migration ends up running
against the wrong database. Note `application.database` and not `application.mongo`: the latter is the
client, and a ledger is written in one database.

Each block accepts `migration(…)` as many times as you like, plus the four knobs `stx.migrations.*`
spells for Spring — `lease`, `lockTimeout`, `lockPoll`, `staleAfter` — and `table` / `collection` for
where the ledger lives.

**`gate(runner)` is still the contract underneath.** The DSL ends in a call to it, so an application
with a ledger of its own keeps building its runner and handing it over, and the two mix in one block:

```kotlin
install(Migrations) {
    sql(application.jpa) { migration(V1Orders()) }
    gate(myOwnRunner)
}
```

`install(Migrations)` **goes after the connection plugins it reads from** — `application.jpa` throws
by name when `install(JpaConnection)` has not happened yet, and Ktor runs install blocks in order.
Runners run in the order they were added; they are separate ledgers with separate locks, so *in
order* is a statement about this process rather than a transaction across two servers.

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

**`store: mongo` on Spring Data needs no `stx.mongo`.** The ledger wants the coroutine driver's
`MongoDatabase`, and `stx-spring-boot` bridges one from the `ReactiveMongoDatabaseFactory` the
application already has. Turning on `stx.mongo` to produce one instead opens a second pool against the
same server — and under test a pool that never saw the harness's per-run database suffix, so the
migrations would run against a database nobody chose and every spec would still pass.

**Reading the ledger back** is `MigrationGate`, the same bean that ran it — the Spring twin of Ktor's
`call.migrations`:

```kotlin
@RestController
class MigrationHealth(private val gate: MigrationGate) {
    @GetMapping("/health/migrations")
    fun applied() = gate.ledger.map { "${it.version} ${it.description} ${it.status}" }
}
```

A snapshot taken as the context refreshed, not a live read: once the gate has passed, only another
process can change the ledger, and this one could not act on the news anyway.

`examples/spring-orders` is the whole path run end to end: two `@Component` migrations over Spring
Data's own pool, the ledger asserted in `OrdersApplicationTest`, and no spec polling for anything.

## A migration must be safe to attempt twice

Not because the runner will — an `APPLIED` record is skipped — but because
[clearing a stuck record](#clearing-it) means somebody deleting it and letting the migration run
again. Write every statement so the second attempt is a no-op rather than an error:

```kotlin
// on both servers
context.execute("create table if not exists orders (…)")
context.update("update orders set currency = 'EUR' where currency is null")

// on PostgreSQL, where `if not exists` also covers indexes and columns
context.execute("create index if not exists orders_currency on orders (currency)")
context.execute("alter table orders add column if not exists currency varchar(3)")

// on MongoDB, where a filtered update is naturally a no-op the second time
database.getCollection<Document>("orders").updateMany(exists("tags", false), set("tags", emptyList()))
```

**On MySQL, an index or a column cannot be added idempotently at all.** Both spellings are syntax
errors there (measured above), and `SqlMigrationSession` has no `select` to check
`information_schema` with first. That is not something the library can fix for you — it is why the
ledger fails closed, and why [clearing a record](#clearing-it) asks you to look at the schema before
deciding.

The practical shape on MySQL: keep such a migration to **one statement**, so *ran* and *did not run*
are the only two outcomes an operator has to tell apart.

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
