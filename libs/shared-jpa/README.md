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

**There is no static metamodel**, and there cannot be: `hibernate-jpamodelgen` is a javac annotation
processor, this toolchain runs Java annotation processing for Java and Android modules only, it has
no kapt, and jpamodelgen has no KSP build. So there is no `Order_` to write queries against —
`KProperty1` stands in for it, which is what the query DSL below is built on.

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

**The strings are injected, so the IDE treats them as HQL and SQL.** Every query parameter here
carries `@Language("HQL")` or `@Language("SQL")`, which buys syntax highlighting, structure and
keyword completion inside the string — in this module and in anything that depends on it, with no
IDE setup and nothing under `.idea`. IntelliJ ships Hibernate injections already, but they are
matched against Java PSI on `QueryProducer.createQuery`, so they fire on neither Kotlin nor a
wrapper; the annotation is what reaches the Kotlin injector.

What it does **not** buy is entity and attribute completion — `from Product p where p.` will not
offer `sku`. That needs a persistence model, which IntelliJ reads from a JPA facet, and this project
has none to read: the bootstrap is programmatic, so there is no `persistence.xml`, and the IDE module
graph comes from Amper rather than from `.iml` files a facet could hang on. For native SQL a data
source in the Database tool window fills the gap properly, since it completes against the real
schema. For entity queries, the typed DSL below is the thing that actually knows the mapping.

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

`select<T>()` builds the same query from the entity's properties instead of a string, and answers
with the query itself: everything that shapes it chains, and the terminals are the ones an HQL query
has.

```kotlin
session
    .select<Purchase>()
    .where { Purchase::total gt 100L }
    .where { join(Purchase::customer)[Buyer::name] eq "ada" }
    .orderBy { desc(Purchase::total) }
    .limit(20)
    .list()
```

**Every call adds; none replaces.** Two `where` calls are one `and`, two `orderBy` calls are two sort
keys in the order written, and a `where` may answer with `null` to add nothing at all — so a query
assembled from filters the caller learns one at a time needs no string concatenation. Nothing runs
until a terminal: `list()`, `first()`, `single()`, `singleOrNull()`, `count()`, or `page(request)`.

**A predicate is written straight off the property, and it is still fully typed.** The receiver of
`eq`, `gt` and the rest is `KMutableProperty1`, not `KProperty1`, and that is the whole design:
`KProperty1<T, out V>` is covariant in the value, so the compiler is free to widen `V` to `Any` and
`Purchase::total eq "nope"` type-checks against a `Long` column. `KMutableProperty1<T, V>` declares
`V` invariantly — it has a setter to accept one — so the same line is a compile error, *actual type
is 'String', but 'Long' was expected*. Kotlin's own standard library solves this with
`@OnlyInputTypes`, which is internal to it.

An entity's attributes are `var`, since Hibernate writes them, so that is the ordinary case rather
than a restriction. Anything reached through a join or a function goes through the path form and the
same operators on `Expression` — `join(Purchase::customer)[Buyer::name] eq "ada"`,
`lower(this[Buyer::name]) eq term` — and so does a `val` attribute.

**A join is remembered, so asking for it twice gives the same join rather than a second one.** That
is what lets `join` be chained like everything else: it can be taken where it is used instead of
being declared ahead of every clause that reads it. Holding it in a `val` still reads better when
several clauses use it, and now means the same thing:

```kotlin
session
    .select<Purchase>()
    .where { join(Purchase::customer)[Buyer::name] eq "ada" }    // taken here…
    .orderBy { asc(join(Purchase::customer)[Buyer::id]) }        // …and the same one here
    .list()
```

`join` takes a to-one association, nullable or not, and `joinEach` a to-many, inferring the element
type from the collection with no reflection at runtime. `JoinType.LEFT` keeps the rows with nothing
to join to, and asking for a join that was already taken as a different type is refused rather than
silently ignored. A `joinEach` returns the owner once per element until `distinct()`.

The block `select<T> { }` still takes is the same query and the same `where` — it is somewhere to put
a `val` for a join, and nothing more. Either form, or a mixture, builds the same SQL; a spec compares
all three.

The vocabulary: `eq` `ne` `gt` `ge` `lt` `le` `within` (a `ClosedRange`, both ends included),
`like` `notLike` `ilike` `oneOf`, `isNull()` `isNotNull()`, and `and` `or` `!` with `all(…)` and
`any(…)` over a list — and `asc`/`desc` take a property the same way. `eq null` is not `is null` — it
renders `= null`, which is never true in SQL, so ask with `isNull()`.

**A restriction can be named and reused.** `JpaSpec<T>` is the type `where` already takes, given a
name — a lambda with the query in scope, answering with a predicate or with `null` to restrict
nothing. Nothing had to be added for `where(spec)` to compile: a Kotlin function type is
contravariant in its receiver, so a spec written against `Joins<T>` fits a selection and a projection
alike.

```kotlin
val large: JpaSpec<Purchase> = { Purchase::total gt 100L }
val adas: JpaSpec<Purchase> = { join(Purchase::customer)[Buyer::name] eq "ada" }

session.select<Purchase>().where(large).list()
session.select<Purchase>().where(large or adas).count()
```

It is the same idea as Spring Data's `Specification<T>`, which is
`(Root<T>, CriteriaQuery<?>, CriteriaBuilder) -> Predicate` — those three arguments are the receiver
here, already carrying the typed vocabulary. `and` and `or` compose two of them, and `or` is the one
that earns its keep: two `where` calls are already `and`ed, and no chain can say `or`. A spec that
answers `null` restricts nothing, so `or` with one of those is still every row rather than the half
the other side would have kept.

`update<T> { }` and `delete<T>()` are the write side. They restrict through the same `where` and end
at `execute()`, which answers with the number of rows touched — and they carry the same warning
`mutate(hql)` does: they go straight to the database, past everything the session knows.

```kotlin
session
    .update<Purchase> { set(Purchase::total, this[Purchase::total] + 10L) }
    .where { Purchase::reference like "P-%" }
    .execute()

session.delete<Purchase>().where { Purchase::total lt 1L }.execute()
```

The assignments are the update's block because they are what an update *is*; a delete has nothing to
assign, so it has no block. An assignment is `set(property, value)`, and the value can be an
expression — which is how a counter is incremented without reading it first: one statement, one round
trip, and correct when two of them run at once. Neither statement can join — that is JPA's rule for a
bulk statement, so the scopes simply do not offer it rather than offering a method that always fails
when Hibernate renders it.

**A bulk statement with nothing restricting it is refused.** `JpaUnrestrictedMutationException`,
unless it says `everyRow()`. HQL allows `delete from Purchase` and so does this — but only out loud,
because a DSL statement is assembled from parts and a `where` adds nothing when its block answers
null. A statement whose every filter turned out not to apply would otherwise be a statement against
the whole table.

On a stateless session, `update(entity)` and `update<T> { }` are both there and both resolve — the
first is the stateless vocabulary, the second is this DSL. That works on the wrapper; on
`Stage.Session` itself a member always beats an extension, which is why the module builds these
through a name of its own rather than through `raw.update`.

`project<T, R> { }` returns something other than the entity — a summary, one column, a count. The
block's last expression is what a row is, which is why this is the one entry point whose block is
required; everything after it is the chain and the terminals every other query has:

```kotlin
class Summary(val reference: String, val buyer: String)

session
    .project<Purchase, Summary> {
        construct(::Summary, Purchase::reference, join(Purchase::customer)[Buyer::name])
    }.where { Purchase::total gt 100L }
    .orderBy { desc(Purchase::total) }
    .list()
```

**The constructor reference types the arguments.** Criteria takes a `Class` and a list of selections
and checks the match when the query is built, which is late; naming the constructor makes the
compiler check it, so a `Long` column where a `String` is wanted — or two arguments of the right
types in the wrong order — is a compile error naming the constructor that did not fit. The reference
is not called at runtime; Hibernate still constructs the row reflectively.

**Each column is named by a property, or by a path where a property cannot reach** — a joined
column, a function, an aggregate. Every mixture of the two is accepted up to four columns, which is
why there are so many `construct` overloads: a property reference is not a `Selection` and Kotlin has
no implicit conversion, so a position that takes both has to be two declarations.

A projection of one column needs none of that, since a path is already a selection:
`project<Purchase, String> { this[Purchase::reference] }`. `groupBy` takes a property or a block, and
`having` is the only place a condition on an aggregate can go — `where` runs before the grouping.

Projections read only the columns they name and put nothing in the persistence context, which is the
reason to reach for one: a list page showing three fields of a wide entity does not need the other
forty.

The function vocabulary is ordinary functions, not scope methods, so they nest the way they read:
`lower` `upper` `trim` `length` `substring` `concat` `abs` `sqrt` `mod` `coalesce` `nullIf`, and the
aggregates `count` `countDistinct` `sum` `avg` `min` `max` `least` `greatest`. The `count()` terminal
is a different thing worth not confusing with the aggregate: that one rewrites the whole query into a
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

`page(request)` is a terminal like `list()`, and answers with a `Page<T>` — the rows plus a
Relay-shaped `PageInfo` of `startCursor`, `endCursor`, `hasNextPage`, `hasPreviousPage`. Both types
are `shared-common`'s, so `shared-mongo`'s `findPage` answers with the same two.

```kotlin
fun query() =
    session
        .select<Purchase>()
        .where { Purchase::total gt 100L }
        .sortBy(Purchase::total, descending = true)
        .sortBy(Purchase::id)

val page = query().page(PageRequest.first(20))

val next = query().page(PageRequest.first(20, page.info.endCursor))   // the same query, built again
```

It pages by **keyset**, not by `offset`. `offset(n)` makes the database walk and discard n rows, so a
page costs more the deeper it is and page 500 is a scan; resuming from the previous page's sort key
costs the same at any depth, and — the part that shows up in production rather than in a benchmark —
does not skip or repeat a row when one is inserted between two requests. A spec inserts one between
two pages and checks exactly that.

**`sortBy`, not `orderBy`.** A cursor is the sort key of the row it points at, so the page has to
read those values back off the row that came out; `orderBy` takes an expression and there is no way
back from one to a value. A query that used `orderBy` is refused rather than quietly paged along a
key its cursors do not carry. Without a `page`, `sortBy` is simply an ordering.

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

## Criteria, where the DSL does not go

The DSL is a Kotlin surface over JPA Criteria, and the Criteria underneath is not hidden. Subqueries,
set operations, window functions and `insert … select` have no spelling in the DSL; they are written
against Hibernate's own builder, and run through the same suspending terminals:

```kotlin
val criteria = session.criteria.createQuery(PurchaseLine::class.java)
val purchase = criteria.from(Purchase::class.java)
val line = purchase.joinEach(Purchase::lines)

criteria.where(purchase[Purchase::reference] oneOf listOf("P-1", "P-9"))
criteria.select(line)

session.query(criteria).list()
```

`session.criteria` is Hibernate's `HibernateCriteriaBuilder` — the one with `sql()`, `ilike` and the
window functions, which the JPA interface `Stage.QueryProducer.getCriteriaBuilder()` declares away.
It is not spelled `criteriaBuilder` because that name is already a member, and a member always beats
an extension.

`query(criteria)`, `mutate(update)`, `mutate(delete)` and `mutate(insert)` are the bridge back:
`list`, `first`, `single`, `count` and `execute` rather than a `CompletionStage` to remember to
await. The insert is Hibernate's `JpaCriteriaInsert`, off `createCriteriaInsertSelect` or
`createCriteriaInsertValues`, and is the one statement the DSL has no form of at all.

**`purchase[Purchase::reference]` rather than `purchase.get<String>("reference")`.** `get`, `join`
and `joinEach` take a `KProperty1` on any `Path` or `From`, not only inside a query scope — so a
hand-written criteria names its attributes the way the DSL does, and every operator in the DSL
(`eq`, `gt`, `oneOf`, `like`, `and`, `or`) is an extension on `Expression`, which a path already is.
Indexing chains across a to-one association the way Criteria does, joining implicitly:
`purchase[Purchase::customer][Buyer::name]`.

Unlike `join` inside a query scope, the one on a `From` is not remembered — two calls are two joins,
as they are in Criteria itself. Hold it in a `val`, which is the shape a hand-built criteria has
anyway.

**This is why the DSL exists at all.** `Book_.title` — the static metamodel Hibernate's own examples
use — is generated by `hibernate-jpamodelgen`, a *javac* annotation processor. This toolchain runs
Java annotation processing for Java and Android modules only, has no kapt, and jpamodelgen has no KSP
build, so `Purchase_` cannot exist here. Without the adapters above, a criteria written by hand names
its attributes with strings that are unchecked until the query runs — worse than the HQL beside it,
which is at least validated against the mapping when the factory boots. `KProperty1` is the metamodel
this repo can have.

## One entity, as an object

`JpaRepository<T, ID>` is the noun that speaks the DSL — what a service holds, a test substitutes,
and a subclass extends with the two or three queries that are specific to an entity.

```kotlin
val purchases = JpaRepository(Purchase::id)

jpa.transaction { session ->
    purchases.insert(session, Purchase(4, "P-4", 10))
    purchases.findAll(session) { Purchase::total gt 100L }
}

class PurchaseRepository : JpaRepository<Purchase, Long>(Purchase::id) {
    suspend fun findByBuyer(session: JpaSession, name: String) =
        findAll(session) { join(Purchase::customer)[Buyer::name] eq name }
}
```

Reads: `findAll`, `findOne`, `findById`, `requireById`, `findByIds`, `findPage`, `count`, `exists`,
`existsById`, `existingIds`. Writes: `insert`, `insertAll`, `update`, `delete`, `deleteById`,
`deleteByIds`. Everywhere a restriction is taken it is a `JpaSpec<T>`, so the same named
specifications compose here as in a bare `select`.

**Nothing names the entity class, because the property reference already does.** A class cannot have
a `reified` type parameter — inside one, `T` is not reifiable and `select<T>()` does not compile — so
a repository has to learn at runtime what it is generic over, and `Purchase::id` carries it. Reading
that needs no `kotlin-reflect`: a property reference compiles to a `CallableReference` whose owner is
a `ClassReference` from the standard library.

It resolves to the entity the reference *names*, not the class that declared the property, which is
the answer a repository wants when an identifier comes from a `@MappedSuperclass`. A spec pins it
with exactly that shape, because getting it wrong would not fail — it would query the wrong table.

Underneath, the repository reaches the value-typed `select`, `project` and `find` through
`session.raw`, the way any caller reaches what the wrapper does not spell. So no public declaration
in `dsl/`, `session/`, `repository/` or `service/` takes a `KClass`: a type argument is how an entity
is named here, everywhere.

**The session is the first argument, not a field**, and that is the shape the confinement rule
forces. A Mongo collection is a long-lived object a repository can hold; a session belongs to the
event loop that opened it and does not outlive its block. So the repository is a singleton the
container builds once and the unit of work arrives per call — which is also what lets two
repositories share one transaction, the ordinary case that a repository holding its own session
could not serve.

**`deleteById` loads the row and removes it** rather than issuing a bulk `delete` on the identifier.
A bulk statement goes straight to the database: no cascade fires, no `@PreRemove` runs, and a copy
already loaded in this session keeps existing. One extra select buys all three back, and the bulk
form is still a `delete(Purchase::class).where { … }` away for a caller who has measured. This is
where the JPA repository and `shared-mongo`'s deliberately differ — Mongo has neither cascades nor a
persistence context to keep honest.

**Stateful sessions only.** A stateless session has no persistence context, so `update` would have
nothing to merge into and `delete` nothing to cascade from. Bulk loading through one is a job for the
DSL directly.

**Wiring one up takes no wiring.** A repository holds an entity class and a property reference and
nothing else — no factory, no session, no lifecycle — so it is an ordinary class to a container, and
the only injectable piece is the `Jpa` that `jpaModule` and `JpaConnection` already provide:

```kotlin
// Koin
single { PurchaseRepository() }
factory { (principal: String?) -> PurchaseService(get(), principal) }

// Ktor DI
dependencies { provide<PurchaseRepository> { PurchaseRepository() } }
```

The service is a `factory` rather than a `single` because its principal is per-request while the
repository is not. `shared-mongo` registers neither of its two either, for the same reason: what a
container has to build is the connection, and that is already provided.

`existingIds` reads one column rather than the entities, which needs the identifier's `Class` — a
property reference does not carry one without `kotlin-reflect`, so it comes from Hibernate's
metamodel. There is no `ensureIndexes` here the way there is in Mongo: the schema is the migration
tool's business, not the repository's.

## The flow every service repeats

`JpaCrudService<T, ID, C, U>` is the create/update/delete flow with the parts that differ left as
hooks. A subclass writes the two methods that are genuinely about its entity:

```kotlin
class PurchaseService(repository: JpaRepository<Purchase, Long>, principal: String? = null) :
    JpaCrudService<Purchase, Long, NewPurchase, EditPurchase>(repository, principal) {

    override suspend fun buildCreate(input: NewPurchase) = Purchase(input.id, input.reference)

    override suspend fun applyUpdate(existing: Purchase, input: EditPurchase) {
        input.reference?.let { existing.reference = it }
    }
}
```

**An update mutates the managed entity; it does not build a statement.** That is the whole
difference from `MongoCrudService`, whose hook returns update operators and whose empty list means
"write nothing". Here the persistence context already knows what changed, so a hook assigning the
value a column already has writes nothing — Hibernate's dirty check decides, and it is better at it
than a hook comparing fields. There is no "changes are empty" branch on this side because there is
nothing for it to do.

**A write outside a transaction is refused.** `session { }` flushes nothing, so `create` there would
build an entity, return it, report success and write no row. `JpaOutsideTransactionException` names
the operation, the entity and the fix. Reads are untouched — they need no transaction.

**`create` and `update` flush.** Not for the write, which the commit would do anyway, but for
*when*: a generated identifier is assigned, and a constraint violation surfaces at the call that
caused it rather than at the end of the transaction where nothing knows which input was to blame. It
is not a `refresh` — a default the database applied is not read back, and `afterCreate` is where that
belongs.

The hooks are `beforeCreate`, `afterCreate`, `beforeUpdate`, `afterUpdate`, `beforeDelete`,
`afterDelete` and `stamp`. `afterUpdate` takes no "previous" argument, unlike the Mongo service's:
the managed instance was mutated in place, so the state before the update is no longer anywhere to
hand over — a hook that needs it copies what it cares about in `beforeUpdate`. `stamp` is where
*who* is recorded, since only the service knows the principal; *when* belongs to the entity, which
is the half Hibernate's own lifecycle callbacks do better.

## Who wrote this row, and when

```kotlin
@Entity
class Note(@Id var id: Long = 0, var text: String = "") : AuditedEntity()
```

`AuditedEntity` is a `@MappedSuperclass` carrying `createdAt`, `lastModifiedAt`, `createdBy` and
`lastModifiedBy` — the four names `shared-mongo`'s `AuditMetadata` uses, so an audit trail answers
the same question whichever store it came from. Mongo nests them under a `metadata` sub-document
because a document has somewhere to nest; a table does not, so here they are four columns.

**The timestamps are Hibernate's and the principal is the service's**, and the split is not
arbitrary. `@PrePersist` and `@PreUpdate` run inside the flush, so *when* is stamped exactly when a
row is really written: an update the dirty check turns into a no-op fires neither callback and moves
no timestamp — which a service comparing fields could not have told apart. A spec pins that. Only a
service knows the principal, so `JpaCrudService.stampCreated` and `stampUpdated` fill in the other
two, and both are `open` for a subclass that records something else.

The timestamps default to the epoch rather than to `now`, because a plausible-looking value is worse
than an obviously unset one: a row written through a stateless session runs no callbacks, and an
epoch stamp says so.

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

No migrations, no repository or CRUD layer, no second-level cache, and one datasource. The first two
are the natural next features; the schema question in particular deserves its own decision rather
than a default chosen here.

The query DSL stops where JPA Criteria keeps going — no subqueries, no set operations, no window
functions, no `insert … select`. Those are written against Hibernate's own builder and run through
the same terminals, which is a deliberate boundary rather than a gap to fill: see *Criteria, where
the DSL does not go*.
