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
com.strange.jpa.repository JpaRepository — the typed operations every entity gets for free
com.strange.jpa.service    JpaCrudService — create/update/delete with hooks, over a repository
com.strange.jpa.audit      AuditedEntity, the who-and-when superclass the service stamps
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

**This file answers *why the library is shaped this way*.** The two vocabularies — what a query may
say and what an entity may say — gain an entry every phase, so they live in
[`docs/jpa-query-dsl.md`](../../docs/jpa-query-dsl.md) and
[`docs/jpa-mapping.md`](../../docs/jpa-mapping.md).

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

## The query DSL — see `docs/jpa-query-dsl.md`

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

The vocabulary — the operators, the joins, the projections, the function list, the two escapes —
is [`docs/jpa-query-dsl.md`](../../docs/jpa-query-dsl.md). It gains an entry every phase, which is
the signal it does not belong here. What stays below is where the DSL deliberately stops.

## A query says what it loads

**Every association in an entity here is `LAZY`, and a query names what it needs with `fetch` or
`fetchEach`.** That is not a tuning preference; it is what Hibernate Reactive makes of the two
alternatives.

There is no transparent lazy loading in a reactive session — loading an association on access would
mean blocking a thread on a second select, and there is no thread to block. So an unfetched `LAZY`
association does not cost a query, it *throws*, inside the session as readily as after it. The
obvious way out is to leave associations at JPA's `@ManyToOne` default, which is `EAGER`, and that
is the N+1 with better manners: it works, it is silent, and it issues a select per distinct owner
behind every query returning more than one row. `FetchJoinTest` counts three rows pointing at three
different owners as **three** secondary fetches eager and **none** fetched, off Hibernate's own
`entityFetchCount` — `prepareStatementCount` reads zero here, since there is no JDBC under the
Vert.x pool.

So the shape of a read in this library is: mark it lazy, say what you want, and get one statement.

```kotlin
session
    .select<Purchase> {
        fetch(Purchase::customer)
        fetchEach(Purchase::lines)
        where { Purchase::total gt 100L }
    }.list()
```

The third answer is often the best one: **a projection reads the columns it names and loads no
entity at all**, so there is nothing to fetch and nothing to lazily initialise. A list page showing
a reference, a total and a buyer's name wants `project`, not `select` with two fetches.

**A load by identifier has no query to hang a join on**, which is where a fetch plan comes in:

```kotlin
val withBuyer = session.entityGraph<Purchase> { add(Purchase::customer) }

session.find(1L, withBuyer)
session.select<Purchase>().graph(withBuyer).list()
```

An `EntityGraph` is the same idea as a fetch join said as a value rather than inline, and it reaches
two places a join cannot: `find` — including `JpaRepository.findById` and `requireById` — and more
than one level of nesting. Being a value is the third thing: one plan applied to a `find` and to a
`select` cannot disagree about what "a purchase with its buyer" means, and a plan built once at
startup serves every request.

`JpaRepository` answers with entities, so the same question reaches it in both directions. By
identifier, it takes a plan. For the multi-row reads, `query(session, spec)` is the seam — a
`JpaSpec` is a `Joins` receiver and deliberately cannot fetch, because the same spec has to fit a
projection, which has no owner to hang a fetch on. A subclass that always needs the buyer gives its
entity a method that says so once.

The refusals are in [`docs/jpa-query-dsl.md`](../../docs/jpa-query-dsl.md) with the rest of the
vocabulary; the one worth knowing here is that `limit`, `offset` and `page` are refused when a query
loads a collection — by `fetchEach` or by a plan that `addEach`-es one — because the database applies
them to the joined rows and hands back a page whose last owner holds part of its collection.

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

**`shared-mongo` appends `_id` where this refuses**, and the difference is deliberate rather than an
oversight in one of them. Mongo's sort arrives as raw JSON from an HTTP client, so there is no
compiler between the caller and a non-unique sort and no line of Kotlin to point at — appending is
the only way to be safe. Here the sort is `sortBy(Purchase::total)` in the caller's own source, so a
message naming the line and what to add costs nothing and teaches the rule instead of hiding it. The
consequence for a service paging the same object out of both stores: the Mongo call takes any sort,
the JPA call takes any sort ending in the identifier, and neither can silently skip a row.

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
`afterDelete`, and `stampCreated` / `stampUpdated`. `afterUpdate` takes no "previous" argument,
unlike the Mongo service's: the managed instance was mutated in place, so the state before the update
is no longer anywhere to hand over — a hook that needs it copies what it cares about in
`beforeUpdate`. The two `stamp` hooks are where *who* is recorded, since only the service knows the
principal; *when* belongs to the entity, which is the half Hibernate's own lifecycle callbacks do
better.

**Every `after*` hook runs before the commit.** The transaction the caller opened is still open, so a
hook that publishes to Kafka, AMQP or an HTTP endpoint publishes a fact a later rollback unmakes.
A cascade or an outbox row is exactly right there — both are writes in the same transaction, and both
are undone with it. Anything that leaves the database is sent after `transaction { }` returns.

**`create`'s flush names the input that failed; it does not let you continue past it.** A failed
flush dooms the transaction, so a caller looping over inputs with a `try/catch` inside gets one
exception it swallowed and a rollback at the end. Import a batch with a transaction per input.

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

## What an entity may say — see `docs/jpa-mapping.md`

Which database, what a column ends up called, how an identifier is generated, `kotlin.time.Instant`
and `kotlin.uuid.Uuid`, JSON columns, and Bean Validation all live in
[`docs/jpa-mapping.md`](../../docs/jpa-mapping.md). That half gains an entry every phase — a
`SqlTypes` code, a strategy, a converter — and this file answers *why the library is shaped this way*
instead, which is roughly constant.

## Configuration and lifecycle

`Jpa.connect` suspends: reading annotations off every entity and building the metadata model is
ordinary blocking work, done on `Dispatchers.IO`. **On the default `SchemaMode.NONE`, nothing
connects there** — the pool opens its first connection when something asks for a session, so a wrong
password is a failed request rather than a failed startup. Any other mode has schema work to do and
therefore connects: `SchemaMode.VALIDATE` turns a schema missing a table back into a startup failure,
which is the point of choosing it when the schema is managed elsewhere — and it should be.
`SchemaMode.NONE` is the default and the only sane answer for a deployment, because a schema is
migrated by something that keeps a history, not by an ORM inferring one from the classes it happens
to have been given. `test/SchemaModeTest.kt` pins both halves; the two statements used to contradict
each other here.

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

No migrations, no second-level cache, and one datasource. The schema question in particular deserves
its own decision rather than a default chosen here: `SchemaMode` exists for tests and scratch
databases, not as a migration story.

A repository and a CRUD service *are* here — `JpaRepository` and `JpaCrudService`, above — and this
list said otherwise for a phase after they shipped.

The query DSL stops where JPA Criteria keeps going — no subqueries, no set operations, no window
functions, no `insert … select`. Those are written against Hibernate's own builder and run through
the same terminals, which is a deliberate boundary rather than a gap to fill: see *Criteria, where
the DSL does not go*.
