# shared-jpa

Hibernate Reactive for a Kotlin coroutine service — JPA mapping over the Vert.x Postgres client,
with no thread parked on a query.

```
com.strange.jpa            Jpa, JpaConfig, JpaException — connect, close, and what this module throws
com.strange.jpa.session    session / transaction / stateless, and the confinement bridge underneath
com.strange.jpa.query      HQL and SQL through one builder, and the one-shot operations on Jpa
com.strange.jpa.convert    the converters JPA has no basic type for — kotlin.time.Instant, kotlin.uuid.Uuid
```

```kotlin
val jpa = Jpa.connect(
    JpaConfig(uri = System.getenv("POSTGRES_URI"), username = …, password = …),
    Order::class, Customer::class,
)

val recent = jpa.session { session ->
    session
        .query<Order>("from Order where customer = :customer order by placedAt desc")
        .parameter("customer", id)
        .limit(20)
        .list()
}

jpa.transaction { session ->
    session.persist(Order(customer = id))
}
```

Nothing above awaits anything. `find`, `get`, `persist`, `merge`, `remove`, `refresh` and `flush`
all suspend and answer with values.

## The rule this library is built around

**A Hibernate Reactive session belongs to the Vert.x context that opened it.** Use it from any other
thread and it throws `HR000069`— the [reference
documentation](https://docs.hibernate.org/reactive/4.5/reference/html_single) quotes `HR000068` for
this, and 4.5.5 does not; `SessionConfinementTest` asserts what the runtime actually says. A
coroutine that suspends inside a session block resumes wherever its dispatcher puts it, which is not
that thread — so the obvious wrapper,

```kotlin
factory.withTransaction { session -> scope.future { block(session) } }.await()   // broken
```

is broken, and it is broken in the way that hurts most: it works in a spec that never suspends, and
fails on the first handler that awaits something in the middle of a transaction.

So every entry point here goes through `confined`, which captures the Vert.x context *inside*
Hibernate's own callback and turns it into a dispatcher that posts every resumption back to it. The
block then runs on one thread from beginning to end, across as many suspension points as it likes.
`SessionConfinementTest` holds both halves: the naive bridge fails with `HR000069`, and
`jpa.transaction { }` keeps the thread across a `delay`.

Two consequences for callers:

- **The block runs on an event loop, so it must not block.** CPU work and blocking I/O go outside the
  block, or inside a `withContext(Dispatchers.IO)` that does not touch the session.
- **The session does not outlive the block.** Do not store it, hand it to another coroutine, or await
  anything from it afterwards.

Cancellation crosses the bridge in the useful direction: cancelling the calling coroutine cancels the
work inside the session, so a request that goes away does not leave a transaction running. A failure
travels the other way — out through the stage Hibernate is waiting on, so `withTransaction` rolls
back and the caller sees the original exception. That asymmetry is why the confined scope is
parented to a supervisor, and the rollback scenario in `SessionsTest` is what settled it.

## Entities are ordinary Kotlin classes

```kotlin
@Entity
@Table(name = "orders")
class Order(
    @Id var id: Long = 0,
    var reference: Uuid = Uuid.random(),
    var placedAt: Instant = Clock.System.now(),
)
```

No `open`, no hand-written no-arg constructor. Two compiler plugins, enabled in `module.yaml`:

```yaml
settings:
  kotlin:
    noArg: { enabled: true, presets: [ jpa ] }
    allOpen:
      enabled: true
      annotations: [ jakarta.persistence.Entity, jakarta.persistence.MappedSuperclass, jakarta.persistence.Embeddable ]
```

`noArg` gives Hibernate the constructor it instantiates rows with; `allOpen` makes the class
non-final so it can be subclassed for a lazy proxy — without it every `@ManyToOne(fetch = LAZY)`
fails at startup. `allOpen` has no `jpa` preset (only spring, micronaut and quarkus), which is why
the three annotations are listed. **Any module that holds entities needs this block**, not just this
one; `shared-ktor` and `shared-koin` carry it for the single fixture entity in each of their test
trees.

There is no static metamodel and no Criteria DSL, because there is no `kapt` in this toolchain and
`hibernate-jpamodelgen` cannot process Kotlin sources without one. HQL is the query language here.

## The session is ours, not Hibernate's

`session { }` and `transaction { }` hand you a `JpaSession`, not a `Stage.Session`. Every operation
on it suspends and returns a value; nothing returns a `CompletionStage`, and there is no `.await()`
to forget.

**That is a wrapper rather than extension functions, and not by preference.** `persist`, `merge`,
`remove`, `refresh` and `flush` are already members of `Stage.Session`, and in Kotlin a member always
beats an extension of the same name and arity. A `suspend fun Stage.Session.flush()` compiles
perfectly and is then unreachable — `session.flush()` still resolves to the member returning
`CompletionStage<Void>`. That was settled with a throwaway file and the compiler rather than reasoned
about; the only way to keep the JPA vocabulary *and* suspend is to be a different receiver.

`find` gains something beyond ergonomics. Hibernate's returns a `CompletionStage<T>` whose `T` is a
platform type, so nothing warns that a missing row is null; here `find` returns `T?` and `get` throws
`JpaNotFoundException`, and choosing between them is a question about the caller rather than the data.

Everything not wrapped is on `session.raw`. The wrapper is a convenience over Hibernate's API, not a
fence around it.

## Four ways in

```kotlin
jpa.session { … }               // read; no transaction, so nothing is flushed
jpa.transaction { … }           // commits when the block returns, rolls back when it throws
jpa.statelessSession { … }      // no persistence context, no dirty checking, no first-level cache
jpa.statelessTransaction { … }
```

Stateless is for volume: a bulk load or an export holds nothing in memory between rows, at the cost
of everything a persistence context buys — no identity, no cascades, no automatic dirty checking.

**`session { }` flushes nothing.** A session flushes at the end of a unit of work if and only if
there is a transaction, so a `persist` or a change to a loaded entity inside a plain `session { }` is
discarded without a word: no error, no warning, no row. It is Hibernate's rule and it is quiet enough
that `SessionsTest` pins both halves of it. Read in `session`, write in `transaction`.

## Queries

`query<R>(hql)` and `nativeQuery<R>(sql)` return the same builder, on a session or a stateless one.
Terminals suspend, so a result is a value:

```kotlin
.list()            // every match
.first()           // the first, or null — limit(1) under it
.single()          // the one there is, or JpaNoResultException / JpaNonUniqueResultException
.singleOrNull()    // …or null; two rows is still an error
.count()           // how many it would return, ignoring limit and offset
```

`mutation(hql)` and `nativeMutation(sql)` are the write side and answer with the number of rows they
touched. Both go straight to the database, past everything the session knows — no cascades, no
`@PreRemove`, and entities already loaded keep the values they had.

**Bind parameters; do not interpolate.** In Kotlin the injection is the spelling that reads
naturally, which is exactly why `parameter(name, value)` is the only way values reach a query here.

**`JpaConfig.schema` does not reach a native statement.** Hibernate qualifies the table it renders
from HQL and sends SQL as written, so a deployment on a non-default schema qualifies it itself.

The one-shot operations on `Jpa` are for the call that has nothing else to do — **one operation, one
transaction**:

```kotlin
jpa.persist(order)
jpa.find<Order>(id)          // or get<Order>(id), which throws JpaNotFoundException
jpa.merge(order)
jpa.removeById<Order>(id)    // answers whether there was anything there
```

Two of these in a row are two transactions. Anything that touches the database twice belongs in a
`transaction { }`.

## Which database

Postgres, MySQL and DB2. Hibernate Reactive names none of them: it picks a driver at runtime from
the URI scheme, so the only thing that changes is `postgresql://`, `mysql://` or `db2://` — the
entity, the session, the transaction and the HQL are the same, which is what `MySqlTest` exists to
show rather than assert in prose.

All three drivers are declared `runtime-only`. They reach an application's runtime classpath and are
kept off its compile classpath, which is right twice over: nothing in this library references a
driver class, and a `PgBuilder` in application code is a second connection pool nobody is managing.
Two unused drivers cost about a megabyte and load no class. `DriversTest` asserts all three are
actually there, since a dependency scope is a claim about a classpath that nothing else would notice
being wrong.

**MySQL 8.4 needs one thing said out loud.** Every account it creates uses `caching_sha2_password`,
whose first authentication requires either TLS the client trusts or the server's RSA public key.
A reactive client given neither drops the connection, and the error —
`ClosedConnectionException: Failed to read any response from the server` — reads like a network
fault. It is an authentication one. `mysqlContainer()` in `shared-testing` moves its `root` account
onto `mysql_native_password` for exactly this reason, and says so at the point it does it.

DB2 is shipped and unproven here: the driver is on the classpath and `DriversTest` covers that, but
no spec has run against a DB2 server, because `icr.io/db2_community/db2` wants a privileged container
and several gigabytes and this repo does not put a machine under that unasked. Point `DB2_TEST_URI`
at one and the same specs are what should run.

## Identifiers

`@GeneratedValue` works as it does anywhere: `AUTO` and `SEQUENCE` both use a sequence on Postgres,
`IDENTITY` works, and `UUID` works **on a `java.util.UUID`**. `GeneratedIdTest` runs all four against
a real server, because Hibernate Reactive is where a generator that needs a round trip has to be a
`ReactiveIdentifierGenerator`, and a strategy that does not work is a bootstrap error rather than
something a mock would show.

**A `kotlin.uuid.Uuid` cannot be the identifier, and `Jpa.connect` refuses one.** Hibernate rejects
an `AttributeConverter` on an `@Id` outright, and the JDBC-bound `UserType` that would otherwise map
it is what Hibernate Reactive's own documentation says not to reach for. Without a converter nothing
fails: the type is serialized, the primary key comes out `bytea`, inserts and reads both work, and
the table is unreadable to every other client of the database. So the check is at `connect`, which is
the last moment it is still preventable. Use `java.util.UUID` for the key; `kotlin.uuid.Uuid` is fine
on every other attribute.

## Instant and Uuid

`kotlin.time.Instant` and `kotlin.uuid.Uuid` are not JPA basic types, and an unmapped type is not
refused — it is serialized. Everything succeeds, and the column holds bytes no other client of that
database can read, compare or index. Two `autoApply` converters are registered with every factory,
so an entity writes the Kotlin types with nothing on the property and gets `timestamp with time zone`
and `uuid`. Each is pinned by a scenario that asks `information_schema` what the column actually is;
a round trip cannot show this, because a mapping that writes a blob reads that blob back and agrees
with itself.

An attribute that wants something else opts out with `@Convert(disableConversion = true)`. An
application's own converters are named in `Jpa.connect(config, entities, converters)` — a
programmatic bootstrap finds no `@Converter` by scanning.

## Validation

Hibernate Validator is on the classpath and exported, so constraints on an entity are checked before
it is written — no configuration, no explicit `Validator`, nothing to call:

```kotlin
@Entity
class Order(
    @Id @GeneratedValue var id: Long = 0,
    @field:NotNull @field:Size(min = 2, max = 64) var reference: String? = null,
)
```

Note `@field:`. A Kotlin constructor property is a parameter, a property and a field at once, and a
constraint annotation that lands on the parameter is one Hibernate never sees.

Whether this works at all was worth asking rather than assuming: Hibernate ORM applies constraints
through event listeners, and Hibernate Reactive replaces the listeners it fires. It keeps them —
`ValidationTest` persists a violating entity and gets a `ConstraintViolationException` with nothing
written, and the alternative would have been a library that silently stores whatever it is handed.

**The constraints reach the schema too.** `@Size(max = 64)` exports as `varchar(64)` rather than the
default 255, which the same spec asserts against `information_schema` — so a constraint is one
statement of a rule rather than two that can drift apart.

`expressly` comes along as a runtime-only dependency. Hibernate Validator interpolates a message like
*"must be between {min} and {max}"* through Jakarta Expression Language and ships no implementation
of one; without it the first constraint is a `NoClassDefFoundError`.

## Configuration and lifecycle

`Jpa.connect` suspends: reading annotations off every entity and building the metadata model is
ordinary blocking work, done on `Dispatchers.IO`. **Nothing connects there** — the pool opens its
first connection when something asks for a session, so a wrong password is a failed request rather
than a failed startup. `SchemaMode.VALIDATE` turns it back into a startup failure when the schema is
managed elsewhere, which it should be: `SchemaMode.NONE` is the default and the only sane answer for
a deployment, because a schema is migrated by something that keeps a history, not by an ORM
inferring one from the classes it happens to have been given.

`JpaConfig` names the settings a deployment actually changes and takes anything else in
`properties`, applied last so it overrides them. Four are worth knowing about: `connectTimeout`, so a
request queued behind an exhausted pool fails visibly instead of hanging; `idleTimeout`;
`statementCacheSize`, which is the cheapest performance setting here and is off in the driver by
default; and `batchSize`, without which a bulk load is one statement per row.

`close()` closes the factory and, if this built it, the Vert.x behind it. It blocks briefly and
boundedly — `close()` cannot suspend, and returning before the pool is shut leaves connections open
on the server. It is idempotent through `CloseGuard`, which is what lets a DI container close it as
well.

The Vert.x instance is ours unless one is handed to `connect`. Hibernate would create its own and
nothing else could reach it — and reaching it is the point, since the confinement bridge needs the
context a session was opened on.

## Integrations

`install(JpaConnection) { … }` in `shared-ktor`, `jpaModule(config, Order::class)` in `shared-koin`.
Both build one factory for the application and close it with it, both take an `instance` something
else built, and both block once at startup because `connect` suspends and neither an `install` block
nor a Koin `single { }` does.

## Tests

Every spec runs against a real Postgres: `POSTGRES_TEST_URI` with `POSTGRES_TEST_USER` and
`POSTGRES_TEST_PASSWORD` when one is already up, a `postgres:18-alpine` container for the run
otherwise, and skipped when neither. MySQL is the same with `MYSQL_TEST_*` and `mysql:8.4`. A mock cannot show a session used from the wrong thread, which
is the failure this library exists to prevent.

**Each spec gets a schema of its own**, created before it and dropped `cascade` after it, with
`hibernate.default_schema` pointing at it. That is the analogue of the per-spec Mongo database and
the Redis namespace, and it is what keeps a run pointed at a real server from touching anything it
did not create.

## Not here

No migrations, no repository or CRUD layer, no `KProperty` query DSL, no second-level cache, and one
datasource. The first two are the natural next features; the schema question in particular deserves
its own decision rather than a default chosen here.
