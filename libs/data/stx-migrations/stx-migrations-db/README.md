# stx-migrations-db

Where the `stx-migrations` ledger lives. One module, a package per store: `mongo/` for MongoDB
through `stx-mongo`, `sql/` for PostgreSQL and MySQL through `stx-jpa`.

**`io.github.softistx:stx-migrations-db`** — [how to depend on it](../../../../docs/consuming.md).

```kotlin
MongoMigrations(database, listOf(V1Seed(), V2Tags())).run()
SqlMigrations(jpa, listOf(V1Orders(), V2OrderIndex())).run()
```

Selection is **by construction**, not by a `when` on a URI scheme. An application calling either of
these has the connection in hand and is holding migrations the compiler already agreed belong to that
store — `MongoMigration` and `SqlMigration` are one-line marker interfaces, and they earn their line:
a generic `Migration<*>` erases, so a Spring `ObjectProvider` could not tell them apart and would
hand every migration to both runners.

## One module, not two

`LedgerContract` is thirteen scenarios written once and run **three** times — MongoDB, PostgreSQL,
MySQL. The runner has one set of rules, and a ledger that read any of them differently would be a
second runner with the same name. Two modules would have two copies of that file, and they would
drift the first time one was fixed. It is `stx-workflow-db`'s argument, and it holds here for the same
reason.

Both backends are **`compile-only`**. A ledger cannot be *constructed* without the library that
provides its connection, so an application using one already depends on it — and an application
migrating only MongoDB never loads a Hibernate class. `./kotlin show dependencies -m
stx-migrations-db` is the check: `hibernate-reactive` and `mongodb-driver-kotlin-coroutine` are in
COMPILE and absent from RUNTIME.

Neither ledger is `AutoCloseable`, and neither closes anything: the application that opened the
`MongoDatabase` or built the `Jpa` is the one that closes it.

## MongoDB

**The version is the `_id`.** That is the faithful translation of `version bigint primary key`, and it
makes `claim` an ordinary `insertOne` whose duplicate-key error *is* the answer. There is no secondary
index, so nothing here can collide with an index somebody else declared — which is the failure the
runner this library replaces had to name its index to avoid.

**Two collections.** `stx_migrations` holds the records, `stx_migrations_lock` holds one document. A
lock is not a migration, and a sentinel living beside the records is one `find()` away from being read
as a version that has run.

**Raw `org.bson.Document`, not a `@Serializable` type**, with the instants written back as BSON dates
— `stx-telemetry-mongo` makes the same choice, and here the payoff is larger: a ledger over plain
documents works with **any** `MongoDatabase` whatever codec registry it carries, which is what lets a
Spring Data application reuse the pool it already has instead of opening a second one.

**No replica set is needed.** `withTransaction` is what needs one, and nothing here uses it; every
guarantee comes from single-document atomicity.

The lock is two fields — `lockedBy`, `lockedUntil` — that one conditional `updateOne` sets, another
extends and a third clears, with `Lease` as the policy around them.

## SQL

**No JDBC, because nothing in this repository has any.** Every statement goes out on a connection
borrowed from the pool Hibernate is already using — `jpa.connection { }`, the seam `stx-jpa` grew for
exactly this. DDL goes unprepared, which is the only way it goes at all; the reads and the conditional
writes are prepared and bound.

**Two tables**, `stx_migrations` and `stx_migrations_lock`, for the reason the two collections give: a
sentinel row would surface in `all()` as a version that has run, and a `version = -1` to hide it is a
convention every reader has to be told about.

**They are qualified with `JpaConfig.schema`.** `NativeDdlTest` in `stx-jpa` measured why: Hibernate
applies `hibernate.default_schema` when it renders a statement from the mapping, and nothing renders
these, so an unqualified name lands in the connection's own `search_path`. **A migration's own
statements are not qualified for it** — it should say where its tables go.

**The instants are `bigint` epoch milliseconds.** The one place this ledger disagrees with the MongoDB
one, and it disagrees because Mongo has a single unambiguous date type and the two SQL servers do not:
MySQL's `timestamp` converts to UTC on the way in and back to the session's zone on the way out,
`datetime` does not, and Postgres has no `datetime` at all. A `bigint` means one thing everywhere.
`to_timestamp(started_at / 1000)` is the reading glasses.

**`poolSize` must be at least two**, and `SqlMigrations` refuses to be built otherwise. A run holds one
connection for the migration's statements and needs a second for the lease watchdog renewing the lock
underneath it; on a pool of one that is a deadlock at startup, and nothing says anything. The message
names the deadlock rather than leaving it to be met.

**A `Jpa` needs at least one entity**, because `Jpa.connect` refuses to build a factory that maps
nothing. This ledger has none of its own — it writes its two tables by hand — so a migration-only
application still has to hand `Jpa.connect` something. In practice an application on `stx-jpa` has
entities; the specs here map one `Probe` nothing ever queries.

### What a SQL migration may say

```kotlin
interface SqlMigrationSession {
    suspend fun execute(@Language("SQL") sql: String)
    suspend fun update(@Language("SQL") sql: String): Int
}
```

`execute` is **sent as written and not prepared**, which is what makes DDL possible: `create index`,
two statements in one call, and a literal `?` used as a Postgres `jsonb` operator all reach the server
unchanged. `update` is prepared, which is the price of a row count.

**Neither takes parameters**, and that is the deliberate part. The two drivers do not agree on how a
placeholder is spelled — `$1` against `?` — so a parameter list here would be a portability hole in
the one API that is supposed to be portable. A migration is authored code, not a request handler: the
values it needs are literals it writes itself, which is exactly what a `.sql` migration file is
everywhere else.

**There is no `select`.** A portable row type across the two transports would have to be positional,
and a read-modify-write loop inside a startup gate is the thing to avoid. An application that
genuinely has to read has its own `Jpa` in scope and can close over it.

### The dialect surface is one function

`SqlDialect` knows one thing: `$1` against `?`. That is all of it, and it is unavoidable once a
statement is written by hand rather than rendered by Hibernate.

It used to know a second thing, and the specs took it away. The first version spelled *insert unless
it is already there* as `on conflict do nothing` against `on duplicate key update` — and MySQL turned
out to be unable to answer the question at all: the Vert.x client sets `CLIENT_FOUND_ROWS`, so an
insert that hit an existing row reports **one** affected row, exactly as a successful insert does. Two
contract scenarios went red on MySQL and green on Postgres, which is the whole reason the contract
runs twice.

The replacement asks the database instead: insert plainly, and when the insert fails, look to see
whether the row is now there. False if it is, rethrow if it is not. That costs one read on a path that
should never be taken — `claim` is only ever called under the migration lock — and it is right on any
server rather than on the two an enum could name.

**PostgreSQL and MySQL are verified; DB2 is out of scope**, and `SqlDialect.of` refuses it by name
rather than letting it be discovered during `create table`. DB2 has no `create table if not exists`,
so a ledger there is a different design and not a third enum entry.

## Tests

`LedgerContract` runs against all three. Beyond it, each store answers what only it can be asked: that
the Mongo `_id` is the version and the collection carries no second index, that the instants really
are BSON dates, that the SQL tables land in `JpaConfig.schema`, that a pool of one is refused — and,
in both, **that a lease nobody renews expires**. That last one is the recovery path and the contract
cannot ask for it: it needs a lock left behind by a holder that never came back, which only a direct
write to the lock row can make.

Servers come from `//libs/core/stx-testing` — the workspace's own when `MONGO_TEST_URI`, `POSTGRES_TEST_*`
or `MYSQL_TEST_*` name them, containers for the run otherwise, and skipped when neither. A database
per Mongo spec, a schema per Postgres spec, a database per MySQL spec.

---

Apache-2.0 · [Contributing](../../../../CONTRIBUTING.md) · [All the libraries](../../../../README.md)
