# stx-r2jdbc reference

What a `stx-r2jdbc` statement may say. The module README is the *why*; this is the vocabulary, and
it is the half that grows — **a new method, a new backend or a new type conversion is documented
here**.

For the argument about when to reach for this instead of `stx-jpa`, see
[`libs/stx-r2jdbc/stx-r2jdbc/README.md`](../libs/stx-r2jdbc/stx-r2jdbc/README.md). For what an ORM
entity may say, see [`jpa-mapping.md`](jpa-mapping.md).

## Connecting

```kotlin
val db = R2jdbc.connect(
    R2jdbcConfig(
        uri = "postgresql://localhost:5432/shop",
        username = System.getenv("DB_USER"),
        password = System.getenv("DB_PASSWORD"),
        schema = "shop",
        poolSize = 10,
    ),
)
```

`R2jdbc` is `AutoCloseable`. Closing it closes the pool, and the Vert.x behind it when it opened
one — hand a `vertx` to `connect` and it closes only the pool. A second close does nothing; that is
`CloseGuard`, and it is what makes the resource safe to hand to a container that closes what it is
given.

### R2jdbcConfig

| property | default | what it does |
| --- | --- | --- |
| `uri` | `postgresql://localhost:5432/postgres` | Names the server **and the backend** — see [Backends](#backends). Reactive spelling, never `jdbc:` |
| `username` | null | Sent separately; the URI does not carry it |
| `password` | null | The same |
| `schema` | null | Set on every connection as it opens, and proved to exist at `connect`. Must be a plain identifier |
| `poolSize` | `10` | Connections held. Must be at least one |
| `connectTimeout` | null | How long to wait for a connection from the pool before failing |
| `idleTimeout` | null | How long an idle connection is kept |
| `statementCacheSize` | null | Prepared statements cached per connection. Off in the driver by default |

Every constraint is checked in `init`, so a value that cannot work fails where it was written rather
than at the first statement. `ConfigTest` is the list.

### Backends

Read off the URI scheme, the way Hibernate Reactive picks a driver — a deployment changes a string
and nothing else.

| scheme | backend | placeholder | schema statement |
| --- | --- | --- | --- |
| `postgres…` | PostgreSQL | `$1`, `$2`, … | `set search_path to "x"` |
| `mysql…`, `mariadb…` | MySQL | `?` | ``use `x` `` |
| `jdbc:…` | refused by name | | |
| anything else | refused | | |

DB2 is absent deliberately. `stx-jpa` names its driver because Hibernate speaks DB2 for free; here
every server costs a placeholder rule, a schema statement and the specs that prove both.

## Running a statement

`Sql` is what every scope hands you, and it has four methods.

| method | returns | prepared | when |
| --- | --- | --- | --- |
| `query(sql, vararg values)` | `List<Row>` | yes | reads |
| `queryOne(sql, vararg values)` | `Row?` | yes | a read that must match at most one row |
| `execute(sql, vararg values)` | `Int` — rows **matched** | yes | insert, update, delete |
| `unprepared(sql)` | `List<Row>` | no | DDL, several statements, a literal `?` |

```kotlin
db.sql.query("select id, title from books where author_id = ? order by id", authorId)
db.sql.queryOne("select title from books where id = ?", id)?.getString("title")
db.sql.execute("update books set title = ? where id = ?", title, id)
db.sql.unprepared("create table books (id int primary key, title varchar(80))")
```

**Parameters are bound, never interpolated.** The values go to the driver as a `Tuple`, so a `'` in
one is a `'` and not a second statement. Arity is checked by the driver, on both servers, with a
message naming both counts.

`queryOne` throws `R2jdbcException` on more than one row rather than returning the first: a
`where id = ?` that matched twice has found a broken key, and returning row one is how it stays
broken.

`execute` counts what the `where` **matched**, not what changed — the same answer on both servers.
An `update` writing a row's existing value back reports `1` on Postgres and on MySQL alike, so a
caller testing for zero is asking *did the row exist*.

### The placeholder

Write `?` everywhere. On Postgres it is rewritten to `$1, $2 …` in order.

A `?` is left alone inside a single-quoted string (`''` and, after an `E`, `\'` both understood), a
double-quoted identifier, a `$tag$` dollar-quoted body, a `--` line comment or a nested block
comment. Write `??` for a literal `?` — Postgres's three `jsonb` operators are spelled with one —
or send that statement through `unprepared`.

A `$1` written by hand is left exactly as written, on both servers, which is what makes it possible
to hand this a Postgres statement copied from somewhere else.

`unprepared` binds nothing, which is the point: with no parameters there is nothing to interpolate,
so a value from outside the program has no way in. It is the door for what the extended protocol
cannot carry — a body with two `;` in it, and some DDL.

## Scopes

| | connection | transaction | for |
| --- | --- | --- | --- |
| `db.sql` | one per statement | none | a single query |
| `db.connection { sql -> }` | one, pinned | none | statements that must see each other |
| `db.transaction { sql -> }` | one, pinned | commit on return, rollback on throw | a unit of work |

```kotlin
db.connection { sql ->
    sql.unprepared("create temporary table batch (id bigint)")
    sql.execute("insert into batch values (?)", id)
}

db.transaction { sql ->
    sql.execute("insert into orders values (?, ?)", id, total)
    sql.execute("update stock set count = count - ? where sku = ?", quantity, sku)
}
```

Both give the connection back however the block ends, under `NonCancellable` — a cancelled coroutine
that skipped the close would leak one out of a fixed-size pool.

**A `db.sql` call inside a `transaction { }` block runs on a different connection**, and therefore
reads the database as it was before the block started. That is the one sharp edge. Statements
belonging to the transaction go through the `sql` the block hands you.

A rollback that itself fails does not replace the exception that caused it: the caller sees what
went wrong, not what went wrong while giving up.

## Reading a row

`Row` is the driver's, and this slice does not wrap it — `row.getString("title")`,
`row.getInteger("id")`, `row.getValue("x")`.

Two differences between the servers are the caller's to absorb until a row mapper covers them, and
both are measured in `DriverContractTest`:

| | Postgres | MySQL |
| --- | --- | --- |
| `select 1 as FooBar` names the column | `foobar` | `FooBar` |
| a `boolean` column reads back as | `Boolean` | `Byte` |

Everything else agreed: `int` → `Int`, `bigint` → `Long`, `double precision` → `Double`, `timestamp`
→ `java.time.LocalDateTime`, a null column → `null`, and a column the row does not have →
`NoSuchElementException` on both.

## What this library does not do

No persistence context, no dirty checking, no cascade, no lazy loading, no entity mapping, no
criteria DSL. Those are `stx-jpa`'s. See the README for why the list is the feature.

## Failures

`R2jdbcException` is thrown when the failure is this library's to report — a URI with no backend, a
`jdbc:` prefix, a schema that is not there, `queryOne` on two rows.

A failure from the server keeps its own type. A `PgException` carries the SQLSTATE and a
`MySQLException` the vendor code, and both are more useful to a caller catching them than a wrapper
would be, so nothing here rethrows a driver exception in this type.
