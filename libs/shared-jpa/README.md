# shared-jpa

Hibernate Reactive for a Kotlin coroutine service — JPA mapping over the Vert.x Postgres client,
with no thread parked on a query.

```
com.strange.jpa            Jpa, JpaConfig, JpaException — connect, close, and what this module throws
com.strange.jpa.session    session / transaction / stateless, and the confinement bridge underneath
com.strange.jpa.query      HQL and SQL through one builder, and the one-shot operations on Jpa
com.strange.jpa.dsl        the same queries built from the entity's own properties, not a string
com.strange.jpa.page       cursor pagination — a page, a request, and the keyset under them
com.strange.jpa.convert    the converters JPA has no basic type for — kotlin.time.Instant, kotlin.uuid.Uuid
com.strange.jpa.json       the kotlinx.serialization mapper behind a JSON column, and the Json it uses
com.strange.jpa.naming     what a column is called when the entity does not say
com.strange.jpa.scan       reading entities and converters off the classpath
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

## Naming the entities, or scanning for them

```kotlin
Jpa.connect(config, Order::class, Customer::class)   // the mapping is the argument list
Jpa.scan(config, "com.acme.orders.domain")           // the mapping is what is on the classpath
```

**Naming them is the safer of the two, and it is the default for that reason.** A class missing from
the list is an `IllegalArgumentException` on the first query that names it, which is late; but a
package name that is wrong — renamed, shaded, relocated by a fat-jar plugin — is *silent*, and a
scan that finds nothing looks exactly like a scan that ran before the classes were there. `scan`
therefore refuses to return a factory that mapped nothing: no `@Entity` under the given packages and
it throws, naming them. That turns the quiet failure into a startup failure, which is the only
version of it worth having.

A scan collects `@Entity`, `@MappedSuperclass` and `@Embeddable`. The last two are belt and braces —
Hibernate maps a superclass and an embedded type from the entity that uses them, so registering
`Order` alone already maps both — and they matter when the pieces live in a package the scan reaches
and the entity does not.

**Converters are the one thing a scan does that naming cannot.** `addAnnotatedClass` finds no
`@Converter`, so a programmatic bootstrap has to be told about every one; `scan` picks up the
`@Converter` classes in those packages as well.

Both forms take the other as an extra: `Jpa.scan(config, packages, entities = listOf(Legacy::class))`
adds the class that lives somewhere the scan does not reach. `packages(…)` in the Ktor plugin and
`jpaScanModule(config, …)` in Koin are the same thing at their own call sites.

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

`mutate(hql)` and `nativeMutate(sql)` are the write side and answer with the number of rows they
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

## The query DSL

`select<T> { }` builds the same query from the entity's properties instead of a string. It answers
with the same builder `query<T>(hql)` does, so the terminals above are the terminals here.

```kotlin
session
    .select<Purchase> {
        val buyer = join(Purchase::customer)
        where { Purchase::total gt 100L }
        where { buyer[Buyer::name] eq "ada" }
        orderBy { desc(Purchase::total) }
    }.limit(20)
    .list()
```

**Every call adds; none replaces.** Two `where` blocks are one `and`, two `orderBy` blocks are two
sort keys in the order written, and a `where` block may answer with `null` to add nothing at all — so
a query assembled from filters the caller learns one at a time needs no string concatenation.

**A predicate is written straight off the property, and it is still fully typed.** The receiver of
`eq`, `gt` and the rest is `KMutableProperty1`, not `KProperty1`, and that is the whole design:
`KProperty1<T, out V>` is covariant in the value, so the compiler is free to widen `V` to `Any` and
`Purchase::total eq "nope"` type-checks against a `Long` column. `KMutableProperty1<T, V>` declares
`V` invariantly — it has a setter to accept one — so the same line is a compile error, *actual type
is 'String', but 'Long' was expected*. Kotlin's own standard library solves this with
`@OnlyInputTypes`, which is internal to it.

An entity's attributes are `var`, since Hibernate writes them, so that is the ordinary case rather
than a restriction. Anything reached through a join or a function goes through the path form and the
same operators on `Expression` — `buyer[Buyer::name] eq "ada"`, `lower(this[Buyer::name]) eq term` —
and so does a `val` attribute.

A join is held as a value and read from as often as the query needs it — the `where`, the `orderBy`,
and the projection when that lands — instead of being re-declared and re-joined each time. `join`
takes a to-one association, nullable or not, and `joinEach` a to-many, inferring the element type
from the collection with no reflection at runtime. `JoinType.LEFT` keeps the rows with nothing to
join to; a `joinEach` returns the owner once per element until `distinct()`.

The vocabulary: `eq` `ne` `gt` `ge` `lt` `le` `within` (a `ClosedRange`, both ends included),
`like` `notLike` `ilike` `oneOf`, `isNull()` `isNotNull()`, and `and` `or` `!` with `all(…)` and
`any(…)` over a list — and `asc`/`desc` take a property the same way. `eq null` is not `is null` — it
renders `= null`, which is never true in SQL, so ask with `isNull()`.

`update<T> { }` and `delete<T> { }` are the write side, answering with the same `JpaMutation`
`mutate(hql)` does — and carrying the same warning: they go straight to the database, past everything
the session knows.

```kotlin
session
    .update<Purchase> {
        set(Purchase::total, this[Purchase::total] + 10L)
        where { Purchase::reference like "P-%" }
    }.execute()
```

An assignment is `set(property, value)`, and the value can be an expression — which is how a counter
is incremented without reading it first: one statement, one round trip, and correct when two of them
run at once. Neither statement can join
— that is JPA's rule for a bulk statement, so the scopes simply do not offer it rather than offering
a method that always fails when Hibernate renders it.

**A bulk statement with nothing restricting it is refused.** `JpaUnrestrictedMutationException`,
unless the block says `everyRow()`. HQL allows `delete from Purchase` and so does this — but only out
loud, because a DSL statement is assembled from parts and a `where` block adds nothing when its block
answers null. A statement whose every filter turned out not to apply would otherwise be a statement
against the whole table.

On a stateless session, `update(entity)` and `update { }` are both there and both resolve — the first
is the stateless vocabulary, the second is this DSL. That works on the wrapper; on `Stage.Session`
itself a member always beats an extension, which is why the module builds these through a name of its
own rather than through `raw.update`.

`project<T, R> { }` returns something other than the entity — a summary, one column, a count. The
block's last expression is what a row is:

```kotlin
class Summary(val reference: String, val buyer: String)

session.project<Purchase, Summary> {
    val buyer = join(Purchase::customer)
    where { Purchase::total gt 100L }
    construct(::Summary, this[Purchase::reference], buyer[Buyer::name])
}.list()
```

**The constructor reference types the arguments.** Criteria takes a `Class` and a list of selections
and checks the match when the query is built, which is late; naming the constructor makes the
compiler check it, so a `Long` column where a `String` is wanted — or two arguments of the right
types in the wrong order — is a compile error naming the constructor that did not fit. The reference
is not called at runtime; Hibernate still constructs the row reflectively.

A projection of one column needs none of that, since a path is already a selection:
`project<Purchase, String> { this[Purchase::reference] }`. `groupBy` and `having` are here too, and
`having` is the only place a condition on an aggregate can go — `where` runs before the grouping.

Projections read only the columns they name and put nothing in the persistence context, which is the
reason to reach for one: a list page showing three fields of a wide entity does not need the other
forty.

The function vocabulary is ordinary functions, not scope methods, so they nest the way they read:
`lower` `upper` `trim` `length` `substring` `concat` `abs` `sqrt` `mod` `coalesce` `nullIf`, and the
aggregates `count` `countDistinct` `sum` `avg` `min` `max` `least` `greatest`. `JpaQuery.count()` is
a different thing worth not confusing with the aggregate: that one rewrites the whole query into a
count of its rows, which is what a pager needs.

Two escapes, for what is not named:

```kotlin
where { function<Double>("similarity", this[Buyer::name], literal(term)) gt 0.3 }
where { sql<Boolean>("? ~ ?", this[Buyer::name], literal("^A")) eq true }
```

`function` calls a database function by name. `sql` is Hibernate's own `sql()`, registered for every
dialect it supports — a SQL fragment dropped into the query with **`?` as a bind parameter, not a
hole to interpolate into**. A value carrying an apostrophe is a value, which is what makes this an
escape hatch rather than a hazard; the fragment itself should still never be built by concatenating
anything a caller supplied. A fragment whose placeholders and arguments disagree is refused here,
with the fragment in the message, rather than by Hibernate's binder later without it.

Underneath it is JPA Criteria: Hibernate renders the SQL, and this is a Kotlin surface over its query
model rather than a second implementation of HQL that would have to learn every dialect's quoting.
When a terminal has to name the query in an exception it renders the tree back to HQL, and only then.

## Pagination

`selectPage<T>(request) { }` returns a `Page<T>` — the rows plus a Relay-shaped `PageInfo` of
`startCursor`, `endCursor`, `hasNextPage`, `hasPreviousPage`. Both are `shared-common`'s, so
`shared-mongo`'s `findPage` answers with the same two types.

```kotlin
val page =
    session.selectPage<Purchase>(PageRequest.first(20)) {
        where { Purchase::total gt 100L }
        sortBy(Purchase::total, descending = true)
        sortBy(Purchase::id)
    }

val next = session.selectPage<Purchase>(PageRequest.first(20, page.info.endCursor)) { … }
```

It pages by **keyset**, not by `offset`. `offset(n)` makes the database walk and discard n rows, so a
page costs more the deeper it is and page 500 is a scan; resuming from the previous page's sort key
costs the same at any depth, and — the part that shows up in production rather than in a benchmark —
does not skip or repeat a row when one is inserted between two requests. A spec inserts one between
two pages and checks exactly that.

**`sortBy`, not `orderBy`.** A cursor is the sort key of the row it points at, so the page has to
read those values back off the row that came out; `orderBy` takes an expression and there is no way
back from one to a value. A paged block that uses `orderBy` is refused rather than quietly paged
along a key its cursors do not carry.

**The last sort key has to be the entity's identifier**, and a sort that does not end in it is a
`JpaPaginationException` naming what to add. Keyset pagination resumes from a key, so the key has to
be unique: sort by a repeated column alone and every row sharing a value is a coin toss between being
served twice and being skipped — a data bug that reads as a UI bug. The identifier is the one column
this library can prove unique, so it is the one it insists on.

`PageRequest.first(n, cursor)` pages forward, `PageRequest.last(n, cursor)` backward; the backward
page runs the sort flipped and reverses the rows, so both directions read the same way round. One row
is fetched beyond the page size, and whether it turned up is the whole answer to *is there another
page* — one row rather than a second query.

A cursor carries the sort it was issued under and is refused by a differently sorted query, because
the alternative is a page cut along the wrong key that comes back plausible and wrong. Encoding is
not encryption: a client can read a cursor, and forging one buys a page starting somewhere else.

Hibernate Reactive has none of this — core's `getKeyedResultList` never reached the reactive
`SelectionQuery` — so the predicate, the cursors and the flip are this module's. There is no row-value
comparison in the criteria builder either, so the keyset predicate expands to the lexicographic
`or`-chain a composite index satisfies with a seek.

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
at one and the same specs are what should run — except the JSON ones, which cannot: `DB2Dialect`
registers no DDL type for `SqlTypes.JSON` at all.

## Column names

`createdBy` is the column `created_by`.

**That is this library's doing, not Hibernate's.** Hibernate keeps the property name and Postgres
folds the unquoted identifier, so on its own it gives you `createdby` — it is Spring that installs a
snake-case strategy, and the two are met together often enough that almost everyone believes
otherwise. A column is read by psql, by a migration and by whoever is looking at the database without
this application in front of them, so it is written the way SQL is written.

**A name you write is used exactly as you wrote it.**

```kotlin
@Entity
class Order(
    @Id var id: Long = 0,
    var createdBy: String = "",                             // created_by
    @Column(name = "lastSeen") var lastSeen: String = "",   // lastSeen, folded by Postgres to lastseen
)
```

This is an `ImplicitNamingStrategy`, which runs where Hibernate is deciding a name it was not given —
not the `PhysicalNamingStrategy` that Spring and Hibernate's own `PhysicalNamingStrategySnakeCaseImpl`
use, which rewrites every identifier including the ones an entity spells out and leaves quoting as the
only way out. For an entity that names nothing the two are identical; they differ only where somebody
said what they wanted. It is also what makes mapping an existing camelCase schema possible without
quoting every identifier in it.

The splitting rule is Hibernate's own, copied so that switching strategies later renames nothing: an
underscore goes where a lower-case letter or digit is followed by an upper-case letter followed by a
lower-case letter or digit. So an acronym stays glued — `orderURL` is `orderurl`. `NamingTest` asserts
that equivalence against Hibernate's class rather than describing it.

`JpaConfig(naming = Naming.AS_WRITTEN)` turns it off, for a schema that already exists and was not
built this way. **Changing this setting renames every column that was not named by hand**, so it is a
decision to make before there is a schema rather than after.

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
application's own converters are named in `Jpa.connect(config, entities, converters)`, because
`addAnnotatedClass` finds no `@Converter` — or found for it by `Jpa.scan`, which is the one job the
scan does that naming the classes cannot.

## JSON columns

A structured value in one column, two ways. **Reach for the first one.**

```kotlin
@Embeddable
class Coordinates(var latitude: Double = 0.0, var longitude: Double = 0.0)

@Entity
class Place(
    @Id var id: Long = 0,
    @Embedded @JdbcTypeCode(SqlTypes.JSON) var at: Coordinates = Coordinates(),
)

jpa.session { it.query<Long>("select p.id from Place p where p.at.latitude > :south").parameter("south", 50.0).single() }
```

An `@Embeddable` needs no `@Serializable` and nothing from this module: Hibernate builds the document
from its own mapping model. That is worth having for what it buys — **HQL paths into it**, checked
against the mapping at startup, and every field keeping its own mapping, so the `Instant` and `Uuid`
converters apply *inside* the document. A shape you know is a shape the database should know.

The second way is for a shape you deliberately do not map — an open-ended payload, a versioned
document, a sealed hierarchy:

```kotlin
@Serializable
data class Address(val street: String, val city: String, val country: String = "DE")

@Entity
class Customer(
    @Id var id: Long = 0,
    @JdbcTypeCode(SqlTypes.JSON) var address: Address = Address("", ""),
    @JdbcTypeCode(SqlTypes.JSON) var tags: Map<String, String> = emptyMap(),
    @JdbcTypeCode(SqlTypes.JSON_ARRAY) var labels: List<String> = emptyList(),
)
```

**This is the part that does not work without this library.** Hibernate resolves the JSON mapper by
looking for Jackson, then Jackson 3, then JSON-B, and none of the three is a dependency here — so
without a mapper of ours the annotation above compiles, exports a `jsonb` column, and throws on the
first write telling a Kotlin codebase to install Jackson. `Jpa.connect` registers a `FormatMapper`
over kotlinx.serialization instead, so `@Serializable` is what makes a JSON column work. Generic
attributes resolve from the reflective type, so `Map<String, String>` keeps its type arguments.

**Two codes, and the wrong one used to be a runtime accident.** `SqlTypes.JSON` is for a document that
is an object, `SqlTypes.JSON_ARRAY` for one that is a list. Both make a `jsonb` column; given the
wrong one the reactive binder wraps the document in the wrong Vert.x type and every write fails with
`DecodeException: Failed to decode` and nothing else. `Jpa.connect` refuses the mismatch at startup
instead, naming the attribute and the code to use.

**What the stored document looks like, and why.** `jpaJson` is `lenientJson` with
`encodeDefaults = true`. kotlinx otherwise omits a property that equals its default, and a `jsonb`
column is read by SQL as well as by the class that wrote it — `address->>'country'` would be null for
exactly the rows whose country happened to be the default, and a functional index over it would miss
them. Nulls stay explicit for the same reason: `jsonb_exists(address, 'note')` and `is null` are
different questions, and only a document that writes the key can answer both. Unknown keys are
ignored on the way in, so a document written by an older version of a class still reads. Override the
whole thing with `JpaConfig(json = …)`.

**Editing a document is not free.** Hibernate's dirty check for a JSON attribute is
`fromString(toString(value))` — a real round trip through the mapper on every check — so an in-place
mutation *is* noticed, at the cost of serializing the document to find out. Keep such a column small,
and prefer a mapped column for anything you filter or sort on.

**Not on DB2.** `DB2Dialect` registers no DDL type for `SqlTypes.JSON`, so schema export fails with
*No type mapping for org.hibernate.type.SqlTypes code: 3001 (JSON)*. Postgres gives `jsonb`, MySQL
`json`, and both are pinned by a scenario asking `information_schema` what the column really is.

Hibernate 7.4 also has HQL `json_value`, `json_query` and `json_exists`, disabled by default behind
`hibernate.query.hql.json_functions_enabled` while they incubate. This library does not enable them
and no spec here has run one — set it through `JpaConfig.properties` if you want them, or query a
document through `nativeQuery` and the database's own operators.

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

`install(JpaConnection) { … }` in `shared-ktor`, `jpaModule(config, Order::class)` in `shared-koin`,
and `packages("com.acme.orders.domain")` / `jpaScanModule(config, "com.acme.orders.domain")` for the
scanning form of each. Both build one factory for the application and close it with it, both take an `instance` something
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
