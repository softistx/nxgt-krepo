# stx-r2jdbc

SQL for a Kotlin coroutine service, over the Vert.x SQL client. A pool, a transaction scope,
parameters that are bound rather than interpolated, and one spelling of a statement that runs on
PostgreSQL and MySQL alike.

```kotlin
val db = R2jdbc.connect(R2jdbcConfig(uri = "postgresql://localhost:5432/shop", username = …, schema = "shop"))

val rows = db.sql.query("select id, title from books where author_id = ?", authorId)

db.transaction { sql ->
    sql.execute("update accounts set balance = balance - ? where id = ?", amount, from)
    sql.execute("update accounts set balance = balance + ? where id = ?", amount, to)
}
```

## Why this exists beside stx-jpa

Not because the ORM is wrong. Because of what the ORM *is*, and what that costs when the connection
underneath it is reactive.

`docs/jpa-mapping.md` records seven traps in `stx-jpa`, and they are not a grab bag: six of them have
a single cause.

| trap | cause |
| --- | --- |
| an unfetched `LAZY` association throws | no thread to block on a second select |
| `@ElementCollection` throws the same way | it is a plural attribute like any other |
| `fetchEach` + `limit` silently truncates | rows stop being owners once you join |
| cascade needs the collection **loaded** | cascade is Hibernate replaying a graph diff |
| Hibernate nulls a child's FK before deleting, unless `@OnDelete` says otherwise | the same |
| a JSON dirty check is a `fromString(toString(value))` round trip | dirty checking compares snapshots |

Every one is a persistence context managing an object graph across a reactive boundary. **A library
that maps rows to values has none of them — not because it is better, but because it does less.**

The seventh is the instructive one, and it is this library's charter. `@SQLDelete` hands a
hand-written statement straight to a driver that is not JDBC, so the statement carries neither the
placeholder dialect nor the schema: `LegacyNote` fails with *syntax error at end of input* because
its `?` reaches Postgres as a literal `?`, and `LegacyDollarNote`, with the placeholder corrected,
fails with *relation "legacy_notes" does not exist* because nothing qualified the name. Neither is
fixable there — `JpaConfig.schema` is a runtime value and an annotation is a compile-time constant.

**A SQL library owns both.** This one does, and those two failures are the specs it was built
against.

## What it deliberately does not have

No persistence context. No dirty checking. No cascade. No lazy loading. No entity mapping. No
criteria DSL.

That list is the feature. A caller who wants those should use `stx-jpa`, which has them and is
documented, specced and in production here. This is the sibling for the cases where the ORM costs
more than it buys — hand-written SQL, projections, batch work, reporting — and the repo had a real
hole exactly there: between `jpa.connection { }`, which hands you a bare `ReactiveConnection` with no
transaction, no schema qualification and a `RowSet` to unpack by hand, and the whole of JPA, there
was nothing.

## Vert.x, not R2DBC

The name says R2DBC and it is not that, which is worth saying once.

- `vertx-pg-client` and `vertx-mysql-client` were already in the catalog, already the drivers under
  `stx-jpa`. R2DBC would be a **second** reactive driver family for the same two databases.
- `stx-testing`'s endpoints already spell their URIs `postgresql://…`; R2DBC wants
  `r2dbc:postgresql://…` and would force a change to a module three libraries share.
- The MySQL 8.4 `caching_sha2_password` trap — which reads as a network fault and is an
  authentication one — is already solved in `stx-testing`'s `ReactiveMySQLContainer`.
- R2DBC's official MySQL driver is abandoned; `io.asyncer:r2dbc-mysql` is a community fork.

JDBC is not a candidate at all: nothing in this repository speaks it, and `Backend.of` refuses a
`jdbc:` URL by name rather than letting it fail somewhere less clear.

## The placeholder

**Write `?` on both servers.** That is the one portability claim this library makes, and it needed
a scanner rather than a `replace` to keep it.

| written | Postgres | MySQL |
| --- | --- | --- |
| `?` | `syntax error at or near "as"` | works |
| `$1` | works | `Unknown column '$1' in 'field list'` |

Measured both ways on both servers in `DriverContractTest`, which also corrected two assumptions on
its first run: `select $1` on Postgres fails on *type inference*, not syntax — it parses, then
refuses an `Int` because there is nothing to infer from — and an `update` that writes a row's
existing value back reports one matched row on **Postgres too**, not only on MySQL where
`CLIENT_FOUND_ROWS` is usually blamed. So `Sql.execute` can promise *matched* on both.

`Placeholders.kt` rewrites `?` to `$1, $2 …` on Postgres, stepping over single-quoted strings
(including `''` and, after an `E`, a backslash escape), quoted identifiers, `$tag$` bodies, `--` line
comments and nested block comments. Postgres also spells three `jsonb` operators with a `?`: write
`??` for a literal one, the escape the Postgres JDBC driver uses, or send the statement through
`unprepared`.

## The three scopes

They differ in *where* a statement runs, not in what you may write — `Sql` is one type for all
three, because `Pool` and `SqlConnection` are both an `SqlClient`.

| | connection | transaction |
| --- | --- | --- |
| `db.sql` | one per statement, handed straight back | none |
| `db.connection { }` | one, pinned for the block | none |
| `db.transaction { }` | one, pinned for the block | committed on return, rolled back on a throw |

`db.sql` is the right answer for a single query and the wrong one for two that have to see each
other — a temporary table, a session `set`, MySQL's `last_insert_id()`. Holding a connection for
longer than a statement is how a reactive pool comes to be sized like a blocking one.

**The transaction is the connection**, and that is the whole mechanism. A row written and not yet
committed is invisible to every other connection in the pool, so a `db.sql.query(…)` *inside* a
`transaction { }` block reads the database as it was before the block started. That is the one sharp
edge here. Statements belonging to the transaction go through the `sql` the block hands you.

## Threads

Unlike `stx-jpa`, there is no confinement machinery, and its absence is measured rather than assumed.

A Hibernate Reactive session belongs to the thread that opened it and says so with `HR000069`, which
is why `stx-jpa` carries `Confinement.kt` and a custom dispatcher. The Vert.x client asserts nothing
of the kind: `TransactionTest` suspends on `Dispatchers.IO` in the middle of a transaction, resumes
on another thread and commits. Two statements issued at once down one connection are legal too —
serialised by the driver rather than run in parallel — so an `async` per statement inside a block
buys nothing and is not an error.

## The schema

`R2jdbcConfig.schema` is set on every connection as the pool opens it, once per connection rather
than once per statement, so a caller qualifies nothing.

It is also **proved at connect**, which is not defensiveness: Postgres accepts
`set search_path to "nope"` against a schema that does not exist, and every query afterwards then
fails with `relation … does not exist` and no hint that the schema was the reason. A connect handler
cannot reject a connection, so the check is a separate question asked of `information_schema`. On
MySQL a schema *is* a database, and it is applied the same way.

Naming a schema is therefore the one thing that makes `connect` reach the server. Leave it null and
nothing connects until something asks — the same bargain `Jpa.connect` makes on `SchemaMode.NONE`.

## Reading further

`docs/r2jdbc.md` is the vocabulary: every method, what it returns, what it binds, and the type each
server hands back. This file is the *why*, and stays roughly this size.
