# stx-jpa

Hibernate Reactive for a Kotlin coroutine service — JPA mapping over the Vert.x Postgres client,
with no thread parked on a query.

**`io.github.softistx:stx-jpa`** — [how to depend on it](../../../../docs/consuming.md).

```
com.softistx.jpa            Jpa, JpaConfig, JpaException — connect, close, and what this module throws
com.softistx.jpa.session    session / transaction / stateless, and the confinement bridge underneath
com.softistx.jpa.query      HQL and SQL through one builder, the CRUD reads and writes, one-shot ops on Jpa
com.softistx.jpa.criteria   extensions on JPA's own Criteria types — properties instead of strings
com.softistx.jpa.convert    the converters JPA has no basic type for — kotlin.time.Instant, kotlin.uuid.Uuid
com.softistx.jpa.json       the kotlinx.serialization mapper behind a JSON column, and the Json it uses
com.softistx.jpa.naming     what a column is called when the entity does not say
com.softistx.jpa.scan       reading entities and converters off the classpath
com.softistx.jpa.audit      AuditedEntity and its stamps — who wrote a row, and when
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
[`docs/jpa-criteria.md`](../../../../docs/jpa-criteria.md) and
[`docs/jpa-mapping.md`](../../../../docs/jpa-mapping.md).

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
one; `stx-ktor` carries it for the single fixture entity in its test
trees.

**There is no static metamodel**, and there cannot be: `hibernate-jpamodelgen` is a javac annotation
processor, this toolchain runs Java annotation processing for Java and Android modules only, it has
no kapt, and jpamodelgen has no KSP build. So there is no `Order_` to write queries against —
`KProperty1` stands in for it, which is what the Criteria extensions below are built on.

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
adds the class that lives somewhere the scan does not reach. `packages(…)` in the Ktor plugin is the
same thing at its own call site.

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

**A `transaction { }` inside a `transaction { }` is not a second one.** It hands back *the same
session* — `inner.raw === outer.raw` — because `Stage.SessionFactory.withTransaction` joins the
transaction already in scope. That is reasonable behaviour, and it is what makes a service method
that opens a transaction safe to call from another that already has one. But it means nesting is not
a way to get a second connection: the inner block commits nothing of its own, and an outer block that
throws afterwards takes the inner block's writes with it.

Anything that genuinely needs two transactions at once needs two `Jpa` instances. `NestedTransactionTest`
pins both halves, and it exists because the fact cost a wrong measurement: a `@DynamicUpdate` spec
written with a nested `transaction { }` as the competing writer failed *by passing*, with the dynamic
and the static mapping agreeing because there had only ever been one transaction.

There is a fifth way in, below all four: `jpa.connection { }` borrows a raw connection for the
statements a session cannot send at all. See *Below the session*.

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
schema. For entity queries, a criteria named by properties is the thing that actually knows the
mapping.

**Bind parameters; do not interpolate.** In Kotlin the injection is the spelling that reads
naturally, which is exactly why `parameter(name, value)` is the only way values reach a query here.

**`JpaConfig.schema` does not reach a native statement.** Hibernate qualifies the table it renders
from HQL and sends SQL as written, so a deployment on a non-default schema qualifies it itself.

## Below the session — `jpa.connection { }`

Two things a session has no verb for: DDL, and a statement Hibernate's parameter recogniser would
refuse. `nativeMutate` is documented for *update, insert or delete*, prepares everything it is given,
and reads a literal `?` as an ordinal parameter — so `create index`, a body with two statements in it
and Postgres's `jsonb ? 'key'` are all outside it. `jpa.connection { }` borrows a connection from the
pool Hibernate is already using and hands it straight back:

```kotlin
jpa.connection { connection ->
    connection.executeUnprepared("create table if not exists ledger (version bigint primary key)").await()
    connection.update("insert into ledger (version) values ($1)", arrayOf<Any?>(1L)).await()
}
```

**It is not a second pool.** `Implementor` is Hibernate Reactive's own integrator SPI, and the
`ReactiveConnectionPool` behind it is the one every session takes its connection from. A
`PgBuilder.pool()` beside the factory would be a second set of connections nobody is sizing and
nobody is closing.

`NativeDdlTest` measures each of the following rather than asserting it from the documentation:

| | through `jpa.connection` | through `nativeMutate` |
| --- | --- | --- |
| `create table` / `create index` | yes | refused |
| two statements in one call | yes | refused |
| a literal `?` as an operator | yes | read as a parameter, refused |
| positional parameters | `update(sql, arrayOf(…))` → row count | `:name` only |
| `JpaConfig.schema` applied | **no** | no |

That last row is the one to write a caller around. An unqualified name lands in the connection's own
`search_path`, which on a schema-per-tenant or schema-per-test deployment is not the schema the
factory was configured with — qualify the table, or issue `set search_path to …` first and keep it
for the rest of the block.

**There is no transaction unless the block opens one.** Each statement commits on its own; a caller
that wants schema work to be all-or-nothing calls `beginTransaction` and `rollbackTransaction`
itself, which on Postgres does undo a `create table` and on MySQL does not, because MySQL's DDL is
not transactional. The connection is closed however the block ends, including on cancellation.

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

## Criteria — see `docs/jpa-criteria.md`

The other way in is JPA's own Criteria API, with the pieces named by properties instead of strings.
`com.softistx.jpa.criteria` adds nothing to the *shape* of a criteria — it is Criteria's statement,
Criteria's `where`, Criteria's `Root` — and replaces only the parts that would otherwise be unchecked
text:

```kotlin
val criteria = session.createQuery<Purchase>()
val purchase = criteria.from(Purchase::class.java)
val buyer = purchase.fetch(Purchase::customer)

criteria.where((purchase[Purchase::total] gt 100L) and (buyer[Buyer::name] eq "ada"))
criteria.orderBy(desc(purchase[Purchase::total]))

session.query(criteria).limit(20).list()
```

**This is why the extensions exist at all.** `Purchase_.total` — the static metamodel Hibernate's own
examples use — is generated by `hibernate-jpamodelgen`, a *javac* annotation processor. This
toolchain runs Java annotation processing for Java and Android modules only, has no kapt, and
jpamodelgen has no KSP build, so `Purchase_` cannot exist here. Without the adapters, a criteria
written by hand names its attributes with strings that are unchecked until the query runs — worse
than the HQL beside it, which is at least validated against the mapping when the factory boots.
`KProperty1` is the metamodel this repo can have.

Everything is an extension on a JPA or Hibernate type, which has one consequence worth stating: there
is nothing to be inside. No scope, no receiver to be in the middle of, no builder that has to be
finished. A restriction is a `Predicate` a function can return and a list can hold; a fetch plan is
an `EntityGraph` a `val` can keep; a join is a `Join` that later paths hang off. Naming any of them
needs a function, not a framework.

The vocabulary — the operators, the joins, the fetch rules, the function list, the two escapes — is
[`docs/jpa-criteria.md`](../../../../docs/jpa-criteria.md). It gains an entry every phase, which is the
signal it does not belong here. What stays below is where the extensions deliberately stop.
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

The one exception is shaped like a DataLoader, and it is a mapping choice rather than a query one. A
caller who cannot know at query time whether an association will be read — a GraphQL resolver, which
loads the parents before the child field is resolved — maps the foreign key a second time as a
read-only column and keys on that. It costs no statement, because the value is already in the row
that loaded the owner. `docs/jpa-mapping.md` has the mapping and the two flags it cannot go
without.

```kotlin
val criteria = session.createQuery<Purchase>()
val purchase = criteria.from(Purchase::class.java)
purchase.fetch(Purchase::customer)
purchase.fetchEach(Purchase::lines)
criteria.where(purchase[Purchase::total] gt 100L)
```

**A fetch answers with the join it made**, so the association it loads is also the one later
restrictions and orderings hang off — one join in the SQL rather than two. That matters because the
reverse does not work: `SqmAttributeJoin` has `isFetched` and `clearFetched` and no `setFetched`, so
a plain join cannot be upgraded afterwards and asking for both emits two joins. Fetch first.

The third answer is often the best one: **a projection reads the columns it names and loads no entity
at all**, so there is nothing to fetch and nothing to lazily initialise. Hibernate takes any result
class with a matching constructor and packages the rows into it — a Kotlin `data class` is what a
Java record is here — and the selection list is matched to its parameters by position and type:

```kotlin
data class PurchaseSummary(val reference: String, val total: Long, val buyer: String)

session.query<PurchaseSummary>(
    "select p.reference, p.total, p.customer.name from Purchase p",
).list()
```

No `select new com.…PurchaseSummary(…)` in the HQL, no constructor expression, no `Tuple` to unpack.
The same holds for a criteria: `createQuery<PurchaseSummary>()` with a `multiselect` of three columns
answers with the same rows. A list page showing a reference, a total and a buyer's name wants this,
not an entity query with two fetches.

**A load by identifier has no query to hang a join on**, which is where a fetch plan comes in:

```kotlin
val withBuyer = session.entityGraph<Purchase>().add(Purchase::customer)

session.find(1L, withBuyer)
session.query<Purchase>("from Purchase").plan(withBuyer).list()
```

An `EntityGraph` is the same idea as a fetch join said as a value rather than inline, and it reaches
two places a join cannot: `find` and `get` on the session, which have no query to join on, and a
stateless session's `get`, which has no second chance at all — and more than one level of nesting. Being a value is the third thing: one plan applied to a `find`
and to a query cannot disagree about what "a purchase with its buyer" means, and a plan built once at
startup serves every request. `add`, `subgraphOf` and `subgraphEachOf` are extensions on JPA's own
`Graph`, so the plan is built by ordinary calls and can be handed around half-built.

Two refusals are worth knowing here rather than in the vocabulary, because nothing enforces them:

- **A collection fetch and a row limit do not compose.** The limit applies to the *joined* rows, so
  three purchases holding 3, 1 and 0 lines under `limit(2)` come back as one purchase holding two of
  its three lines — silently incomplete, and cached that way for the rest of the session. An entity
  graph naming a collection truncates identically. Both are measured in specs rather than described.
- **A fetch has no place in a projection.** `select reference, customer` over a fetched join is a
  `SemanticException`: a fetch says *fill this object in*, and a projection is not returning the
  object to fill. Use a plain `join` there.
## The criteria every entity would repeat

There is no repository class and no CRUD service base class here. What one of those would have
offered is a set of extensions on the session, so a service holds nothing but its principal and
takes the unit of work per call:

```kotlin
jpa.transaction { session ->
    session.insert(Purchase(4, "P-4", 10))
    session.findAll<Purchase> { it[Purchase::total] gt 100L }
}
```

Reads: `findAll`, `findOne`, `findPage`, `count`, `exists`, and `select` for the query itself.
By identifier: `find` and `get` on the session, plus `findByIds`, `existsById` and `existingIds`,
which are told which property the identifier is — `session.existingIds(Purchase::id, ids)`. Writes:
`insert`, `insertAll`, `update`, `delete`, `deleteById`, `deleteByIds`.

Everywhere a restriction is taken it is a `JpaSpec<T>`, which is `(Root<T>) -> Predicate?` — a plain
function type over Criteria's own root, so a named restriction composes with `and`/`or`, folds with
`all(…)`, and drops into a hand-written criteria unchanged. A spec answering `null` restricts
nothing, which is what a filter a request did not ask for should mean.

**Extensions rather than a base class, because `T` can be reified and a class cannot.** Inside a
`class Repository<T>`, `T` is not reifiable, so such a class has to be told at runtime what it is
generic over — a `KClass`, or a property reference to read an owner off. `session.findAll<Purchase>()`
is resolved by the compiler at the call site, which is both less to pass and less to get wrong. The
identifier-shaped reads still take `Purchase::id`, because they restrict on that column and need its
name.

**It is boilerplate removal, not a layer.** `select<T>(spec) { criteria, root -> … }` hands back the
same `CriteriaQuery` and `Root` a caller would have built, so a fetch join, an ordering or a second
restriction goes there and nothing is hidden. `findAll` and `findOne` deliberately take no `shape`,
so that a trailing lambda means the restriction — the thing callers write nine times in ten.

**The session is the first thing, not a field**, and that is the shape the confinement rule forces. A
Mongo collection is a long-lived object something can hold; a session belongs to the event loop that
opened it and does not outlive its block.

**Every write verb refuses a session with no transaction.** `session { }` flushes nothing, so a
`persist` there reaches no table and a `deleteById` would answer `true` for a row it did not delete;
`JpaOutsideTransactionException` names the operation and the entity instead. `persist`, `merge` and
`remove` on the session stay unguarded — they are JPA's own primitives and promise only that the
instance is managed, which is true. The reads are unguarded too, because reading outside a
transaction is an ordinary thing to want.

**`deleteById` loads the row and removes it** rather than issuing a bulk `delete` on the identifier.
A bulk statement goes straight to the database: no cascade fires, no `@PreRemove` runs, and a copy
already loaded in this session keeps existing. One extra select buys all three back, and the bulk
form is still a `createDelete<Purchase>()` away for a caller who has measured.

**`findPage` cuts by `limit` and `offset`**, asks for one row beyond the page to answer *is there
another*, and leaves the cursors in `PageInfo` null — they belong to keyset pagination, which this is
not. Offset makes the database walk and discard, so a deep page costs more and a row inserted between
requests shifts the window. Fine behind a UI over a few hundred rows; for an export or an infinite
scroll, order by the identifier and resume from the last one seen.

**Writing is stateful-session only.** A stateless session has no persistence context, so `update`
would have nothing to merge into and `delete` nothing to cascade from. The reads take the narrower
`JpaQueries`, so a stateless session gets them too.

### Why there is no service base class

The write flow — read it, check it exists, write it, stamp who did it — was a `JpaCrudService<T, ID,
C, U>` with `buildCreate`/`applyUpdate` abstract and six `before`/`after` hooks. It is gone, and a
service now writes its own `create`:

```kotlin
class PurchaseService(private val principal: String? = null) {
    suspend fun create(session: JpaSession, input: NewPurchase): Purchase {
        val purchase = Purchase(input.id, input.reference).stampedBy(principal)
        session.insert(purchase)
        session.flush()
        return purchase
    }
}
```

That is longer than an overridden `buildCreate` and says more: the order is visible, there is no
`super` call to remember, and the hooks that were the least reusable part of the base class are
simply the lines around the write. What the library keeps is the part worth not getting wrong by
hand — the transaction guard above, and the audit stamp below.

Two things the old base class knew are worth keeping in mind now that the flow is yours:

- **Flush after a create.** It assigns a generated identifier and puts a constraint violation at the
  call that caused it rather than at the commit, where nothing can say which input it was. It is not
  a way to catch one create and continue with the next: a failed flush dooms the transaction, so a
  batch import wants a transaction per input, not a `try`/`catch` per input.
- **Anything that leaves the database goes after `transaction { }` returns.** A cascade or an outbox
  row belongs inside, because both are undone with a rollback; a Kafka message published inside
  cannot be unpublished by one.

**An update mutates the managed entity; it does not build a statement.** That is the whole difference
from Mongo, where an update is a list of operators and an empty list means "write nothing". Here the
persistence context already knows what changed, so assigning the value a column already has writes
nothing — Hibernate's dirty check decides, and it is better at it than any comparison written by
hand.

## Who wrote this row, and when

```kotlin
@Entity
class Note(@Id var id: Long = 0, var text: String = "") : AuditedEntity()
```

`AuditedEntity` is a `@MappedSuperclass` carrying `createdAt`, `lastModifiedAt`, `createdBy` and
`lastModifiedBy` — the four names `stx-mongo`'s `AuditMetadata` uses, so an audit trail answers
the same question whichever store it came from. Mongo nests them under a `metadata` sub-document
because a document has somewhere to nest; a table does not, so here they are four columns.

**The timestamps are Hibernate's and the principal is the service's**, and the split is not
arbitrary. `@PrePersist` and `@PreUpdate` run inside the flush, so *when* is stamped exactly when a
row is really written: an update the dirty check turns into a no-op fires neither callback and moves
no timestamp — which a service comparing fields could not have told apart. A spec pins that. Only a
caller knows who is acting — there is no ambient principal on a Vert.x context — so `stampedBy` and
`touchedBy` fill in the other two, and both are extensions that answer with the entity so the call
chains: `session.insert(Purchase(…).stampedBy(principal))`. A null principal stamps nothing rather
than writing an empty name over a real one, and writing through a session without calling either
leaves them empty, which is the honest answer.

One consequence follows and is pinned too: assigning `lastModifiedBy` on a row that changed nothing
else *is* a change, so `@PreUpdate` fires and `lastModifiedAt` moves. That is the intended reading —
the row records who touched it last, and somebody did — but "a no-op moves nothing" holds only while
the principal is unchanged.

The timestamps default to the epoch rather than to `now`, because a plausible-looking value is worse
than an obviously unset one: a row written through a stateless session runs no callbacks, and an
epoch stamp says so.
## What an entity may say — see `docs/jpa-mapping.md`

Which database, what a column ends up called, how an identifier is generated, `kotlin.time.Instant`
and `kotlin.uuid.Uuid`, JSON columns, and Bean Validation all live in
[`docs/jpa-mapping.md`](../../../../docs/jpa-mapping.md). That half gains an entry every phase — a
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

`install(JpaConnection) { … }` in `stx-ktor`, and `packages("com.acme.orders.domain")` for the
scanning form. It builds one factory for the application and closes it with it, takes an `instance`
something else built, and blocks once at startup because `connect` suspends and an `install` block
does not.

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
databases, not as a migration story. That story is `stx-migrations`, which keeps a history and runs as
a startup gate — it borrows this library's pool through `jpa.connection { }` and adds nothing to it.

**No keyset pagination**, and that is the one that used to be here. Resuming from the previous page's
sort key needs a query object that can be rebuilt per page and read those keys back off a row, which
is a layer above a criteria rather than a part of one. `findPage` cuts by `limit` and `offset`
instead, and says plainly what that costs. For a deep page or a table being written to, order by the
identifier and resume from the last one seen — three lines against `query(session)`, and the same
cost at any depth.

There is no query builder of this module's own at all. What a query may say is what JPA Criteria and
HQL say — subqueries, set operations, window functions, `insert … select` included — and this module
contributes the names, the terminals, the exceptions, and the handful of criteria every entity would
otherwise repeat.

---

Apache-2.0 · [Contributing](../../../../CONTRIBUTING.md) · [All the libraries](../../../../README.md)
