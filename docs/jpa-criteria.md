# What a stx-jpa query may say

The vocabulary `com.softistx.jpa.criteria` adds to JPA's Criteria API — an operator, a function, an
escape. This is the half of `libs/data/stx-jpa` that gains an entry every phase, so it lives here
rather than in the module README, which answers *why the library is shaped this way* and stays
roughly the size it is.

`libs/data/stx-jpa/stx-jpa/README.md` has the rest: the confinement rule, the four ways in, and why a query
says what it loads. `docs/jpa-mapping.md` is the other half of this one — what an *entity* may say.

**Everything here is an extension on a JPA or Hibernate type.** There is no builder of this module's
own, no scope to be inside, and nothing that has to be finished before it is worth anything. A
criteria is Criteria's; what these add is that its attributes are named `Purchase::total` instead of
`"total"`, and that the result reaches a suspending terminal instead of a `CompletionStage`.

## The statement

```kotlin
val criteria = session.createQuery<Purchase>()
val purchase = criteria.from(Purchase::class.java)

criteria.where(purchase[Purchase::total] gt 100L)
criteria.orderBy(desc(purchase[Purchase::total]))

session.query(criteria).limit(20).list()
```

`createQuery<R>()`, `createUpdate<E>()`, `createDelete<E>()`, `createInsert<E>()` and
`createInsertSelect<E>()` are the five statements, reified so the type comes from the variable rather
than from a class literal that can quietly stop matching it. They are on a session, on a stateless
one, and on the `Stage.SessionFactory` — **a criteria needs no connection**, so a statement assembled
once at startup can be handed to a session per request.

`session.criteria` is Hibernate's `HibernateCriteriaBuilder` — the one with `sql()`, `ilike` and the
window functions, which the JPA interface `Stage.QueryProducer.getCriteriaBuilder()` declares away.
It is not spelled `criteriaBuilder` because that name is already a member, and a member always beats
an extension. `Expression<*>.builder` gets the same object back off any node already in hand, which
is what keeps every operator below an ordinary extension rather than a method on something.

`query(criteria)`, `mutate(update)`, `mutate(delete)` and `mutate(insert)` are the bridge back:
`list`, `first`, `single`, `singleOrNull`, `count` and `execute`, all suspending.

## Paths and joins

```kotlin
purchase[Purchase::reference]                      // a column
purchase[Purchase::customer][Buyer::name]          // chained, joining implicitly
purchase.join(Purchase::customer)                  // a to-one join, INNER by default
purchase.joinEach(Purchase::lines, JoinType.LEFT)  // a to-many join, once per element
```

**`purchase[Purchase::reference]` rather than `purchase.get<String>("reference")`.** `get`, `join`
and `joinEach` take a `KProperty1` on any `Path` or `From`, so a criteria names its attributes the
way a static metamodel would have: checked by the compiler, renamed by the IDE, and wrong at compile
time rather than at execution.

**Indexing rather than `Purchase::total eq 100L`, and the reason is not taste.** `KProperty1<T, V>`
is covariant in `V`, so the compiler may widen `V` to `Any` and `Purchase::total eq "nope"`
type-checks against a `Long` column — the mistake this exists to catch, passed straight through to a
runtime failure inside Hibernate. `Path<V>` is invariant, so once the property has been turned into
one the value has nowhere to widen to and the same line is a compile error. Kotlin's own standard
library solves this with `@OnlyInputTypes`, which is internal to it.

`joinEach` infers the element type from `KProperty1<T, Collection<E>>` with no reflection at runtime.
Nothing is remembered: two calls are two joins, as they are in Criteria itself. Hold it in a `val` —
which is the shape a criteria has anyway, since the join is what the later paths hang off.

**`distinct()` is about the SQL, not about the rows that come back.** A `joinEach` does duplicate the
owner once per element in the result set — but an entity query de-duplicates by identity before it
answers, on Hibernate 7, so a query over a to-many join gives each purchase once with or without it.
A *projection* over the same join sees every row: three purchases holding three, one and no lines are
three entities and five projected rows. `FetchJoinTest` measures both.

## The operators

`eq` `ne` `gt` `ge` `lt` `le` `within` (a `ClosedRange`, both ends included), `like` `notLike`
`ilike` `oneOf`, and `and` `or` with `all(…)` and `any(…)` over a list. Each takes a value or another
expression, so a column compares to a column as readily as to a constant.

```kotlin
criteria.where((purchase[Purchase::total] within 100L..500L) and (buyer[Buyer::name] ilike "ad%"))
```

`eq null` is not `is null` — it renders `= null`, which is never true in SQL. Criteria's own
`isNull()` and `isNotNull()` are members of `Expression` already and are what to reach for; this
package adds no second spelling of them.

`all` and `any` fold a list down to one predicate and answer `null` for an empty list, which is what
a request that turned out to narrow nothing should mean. That is the whole of what a named
specification needs here: a restriction is a `Predicate`, so naming one is a function returning
`Predicate` and composing them is `and`. `examples/jpa-shop` has three.

`asc` and `desc` take any expression, and there is a two-argument form saying where the nulls go —
worth spelling out whenever it matters, because the default is the *database's* and they disagree:
Postgres sorts nulls last ascending, MySQL sorts them first. A page boundary landing in the nulls is
a different row on each.

## Functions, and the two escapes

Ordinary top-level functions rather than members of anything, so they nest the way they read —
`length(trim(buyer[Buyer::name]))`:

`lower` `upper` `trim` `length` `substring` `concat` `abs` `sqrt` `mod` `coalesce` `nullIf`, and the
aggregates `count` `countDistinct` `sum` `avg` `min` `max` `least` `greatest`. The `count()` terminal
is a different thing worth not confusing with the aggregate: that one rewrites the whole query into a
count of its rows, which is what a pager needs.

The arithmetic operators `+` `-` `*` `/` are on `Expression<N : Number>`, which is how a counter is
incremented without reading it first.

For what is not named, two escapes on the builder:

```kotlin
cb.function<Double>("similarity", buyer[Buyer::name], cb.literal(term)) gt 0.3
cb.sql<Boolean>("? ~ ?", buyer[Buyer::name], cb.literal("^A")) eq true
```

`function` calls a database function by name. `sql` is Hibernate's own `sql()`, registered for every
dialect it supports — a SQL fragment dropped into the query with **`?` as a bind parameter, not a
hole to interpolate into**. A value carrying an apostrophe is a value, which is what makes this an
escape hatch rather than a hazard; the fragment itself should still never be built by concatenating
anything a caller supplied. A fragment whose placeholders and arguments disagree is refused here,
with the fragment in the message, rather than by Hibernate's binder later without it.

The fragment is rendered inline and **unparenthesised**, so an operator inside it meets whatever
surrounds it at the database's precedence. `sql<Boolean>("? ~ ?", …) eq true` is fine because `~`
binds tighter than `=`; the same line with `>` in place of `~` is a syntax error, because `(a > b) =
c` needs the parentheses it does not get. Put the comparison inside the fragment when the operator is
one the surrounding expression could swallow.

## Fetch joins, and the N+1

```kotlin
val buyer = purchase.fetch(Purchase::customer)     // to-one
val lines = purchase.fetchEach(Purchase::lines)    // to-many, whole
```

`fetch` and `fetchEach` load the association in the same statement as the owner. **In Hibernate
Reactive this is not an optimisation, it is the only way**: there is no transparent lazy loading —
no thread to block on the second select — so reading a `LAZY` association that was not fetched
throws, inside the session as readily as after it. Leaving associations `EAGER` to dodge that is the
N+1 by another name: three rows pointing at three different owners cost **three secondary fetches**
with JPA's `@ManyToOne` default and **none** with a fetch join, which is what `FetchJoinTest` counts
off Hibernate's own `entityFetchCount`.

A fetch join is the answer when the query knows what will be read. A GraphQL query does not — the
parents are loaded before anything knows whether the child field was selected — so fetching always
loads what nobody asked for. There the answer is a mapping rather than a query: the foreign key read
a second time as a plain column, which costs no statement and is what a `@BatchMapping` keys its
DataLoader on. [`docs/jpa-mapping.md`](jpa-mapping.md) has it, including the way it goes stale.

The rules, each pinned by a spec:

- **They default to `JoinType.LEFT`**, where `join`/`joinEach` default to `INNER`. A join is a
  filter; a fetch is about loading, and an inner fetch quietly drops every owner with no children.
  Pass `JoinType.INNER` to mean it.
- **What comes back is a `Join`**, because Hibernate's fetch node is a join too — so a fetched
  association filters, orders and indexes like any other, and the query does not join twice to do
  both. Filtering *through* a fetched collection is the one trap: the surviving rows become the whole
  collection as far as the persistence context knows, so it reads back incomplete and says nothing.
  Take a separate `joinEach` when both are wanted.
- **A fetch after a plain join on the same attribute cannot be one.** Hibernate has `isFetched` and
  `clearFetched` and no way to set it, so a join already taken cannot become a fetch, and asking for
  both emits two joins to the same table. Fetch first, and let the value it returns be the join.
- **`limit` and `offset` do not compose with a `fetchEach`, and only `findPage` refuses the combination.**
  The database applies them to the joined rows, not to the owners: measured, `limit(2)` over three
  purchases holding three, one and no lines answers with *one* purchase holding *two* of its three
  lines — fewer owners than asked for, one of them silently incomplete and cached as whole, and a
  page that reports no next page while owners remain. Page the owners first and fetch their
  collections in a second query off the ids, or project the columns the page actually shows. A to-one
  `fetch` multiplies no rows and is left alone. `select(...).limit(...)` and an entity graph naming a
  collection do not check, so there the rule is yours to keep. `findPage` does, and says so:

  ```kotlin
  session.findPage<Purchase>(limit = 20) { criteria, purchase ->
      purchase.fetchEach(Purchase::lines)
      criteria.orderBy(asc(purchase[Purchase::id]))
  }
  // IllegalArgumentException: findPage cannot fetch the collection 'lines': limit and offset cut the
  // joined rows, not the owners ... Page the owners and load their collections separately, or project.
  ```

  The pattern that works is two queries — the page of owners, then their collections off the ids:

  ```kotlin
  val page = session.findPage<Purchase>(limit = 20) { c, p -> c.orderBy(asc(p[Purchase::id])) }
  val ids = page.data.map { it.id }
  val lines = session.findAll<PurchaseLine> { it[PurchaseLine::purchase][Purchase::id] oneOf ids }
  ```

- **Not in a projection.** A projection has no owner in its select list to hang a fetch on, so
  Hibernate refuses one with a `SemanticException`. Use a plain `join`.
- **One level.** There is no fetch from a fetch. Two levels is what an entity graph is for.

## Entity graphs, when a fetch join cannot reach

```kotlin
val withBuyer = session.entityGraph<Purchase>().add(Purchase::customer)

session.find(1L, withBuyer)                                  // no query to hang a join on
session.query<Purchase>("from Purchase").plan(withBuyer).list()
```

A graph is a fetch *plan*: what to load, said once and applied where it is needed. On a query it does
what `fetch` does, so it is not a second spelling of the same thing — it earns its place in three
places a fetch join does not reach.

- **Loading by identifier.** `find` and `get` on either session have no query
  to join on. Both session types take one, and the stateless one needs it most: with no persistence
  context there is nothing to initialise after the fact.
- **Depth.** `subgraphOf` and `subgraphEachOf` nest as far as the mapping does, where `fetch` stops
  at one level.
- **Being a value.** A plan is named, held in a `val`, and applied to a `find` and a query that then
  cannot disagree about what "a purchase with its buyer" is. It outlives the session that built it —
  it is keyed to the factory's mapping — so one built at startup serves every request.

```kotlin
val plan = session.entityGraph<PurchaseLine>()
plan.subgraphOf(PurchaseLine::purchase).add(Purchase::customer)
```

`add`, `subgraphOf` and `subgraphEachOf` are extensions on JPA's own `Graph`, so a plan is built by
ordinary calls: each returns the level it just made, and the plan is a value that can be handed
around half-built. `subgraphEachOf` is the one for a collection, because JPA's `addSubgraph` on a
plural attribute describes the *collection* and `addElementSubgraph` describes its elements.

The rules, each pinned by `EntityGraphTest`:

- **A plan holding a collection truncates under a limit**, for the same measured reason a `fetchEach`
  does, and just as silently. A plan of to-ones multiplies no rows and pages normally.
- **It is a fetch graph, not a load graph.** An association the mapping declares `EAGER` and the plan
  does not name becomes lazy for that query, and reading it then throws. That is one more reason for
  the rule in the module README: mark every association `LAZY` and say what each query needs, and
  there is nothing eager left to lose.
- **`plan` takes a graph of the query's own result type.** Hibernate's `setPlan` says so, and a
  projection's rows are not the entity — the type says so before the runtime has to.

## Specs, and a typed null

A `JpaSpec<T>` is a restriction with a name: a plain `(Root<T>) -> Predicate?`, so more than one
query can ask for it. `and`, `or` and `all(list)` combine them, and a spec answering `null` restricts
nothing.

```kotlin
val available: JpaSpec<Product> = { it[Product::discontinued] eq false }
fun named(term: String): JpaSpec<Product> = { it[Product::name] ilike "%$term%" }

session.findAll<Product>(available and named("anvil"))
```

`and` and `or` drop a side that answers `null` and keep the other; two `null` sides answer `null`.
That is the right rule for `and`, where a filter that does not apply adds no restriction. For `or` it
is a trap: `null or b` is `b`, so an absent optional filter OR'd with `b` **narrows to `b`'s rows**
instead of widening to every row.

```kotlin
fun named(term: String?): JpaSpec<Product> = { term?.let { t -> it[Product::name] ilike "%$t%" } }
val cheap: JpaSpec<Product> = { it[Product::price] lt 10L }

session.findAll<Product>(named(null) or cheap)  // only the cheap products, not every product
```

When "no restriction" is what an absent filter should mean, keep the optional filter out of the `or`
and decide before composing:

```kotlin
val either = if (term == null) null else named(term) or cheap  // null: every row
session.findAll<Product>(either)
```

`JpaSpecOrTest` pins this against a database.

`cb.nullLiteral<String>()` is a typed null for the places an expression is wanted and there is no
value, in place of JPA's `nullLiteral(String::class.java)`.

## Projections

**Hibernate packages rows into any class with a matching constructor.** Pass the result class to the
query and the selection list is matched to the constructor's parameters by position and type — no
`select new com.…Summary(…)` in the HQL, no constructor expression, no `Tuple` to unpack. A Kotlin
`data class` is what a Java record is here.

```kotlin
data class PurchaseSummary(val reference: String, val total: Long, val buyer: String)

session.query<PurchaseSummary>(
    "select p.reference, p.total, p.customer.name from Purchase p",
).list()
```

The same holds for a criteria, where the result class is the statement's own type:

```kotlin
val criteria = session.createQuery<PurchaseSummary>()
val purchase = criteria.from(Purchase::class.java)
val buyer = purchase.join(Purchase::customer)

criteria.multiselect(purchase[Purchase::reference], purchase[Purchase::total], buyer[Buyer::name])
```

`groupBy` and `having` are Criteria's own, and `having` is the only place a condition on an aggregate
can go — `where` runs before the grouping. A projection of one column needs no class at all:
`query<String>("select reference from Purchase")`.

`JpaQuery.readOnly()` marks the results of any query read-only: a stateful session then keeps no
snapshot of what it loaded, which is half the memory and no dirty check. It is for reads that will not
be changed, and projections already have the same effect.

Projections read only the columns they name and put nothing in the persistence context, which is the
reason to reach for one: a list page showing three fields of a wide entity does not need the other
forty, and it has no association to fetch or to lazily initialise.

## Bulk update and delete

```kotlin
val statement = session.createUpdate<Purchase>()
val purchase = statement.from(Purchase::class.java)

statement.set<Long>(purchase[Purchase::total], purchase[Purchase::total] + 10L)
statement.where(purchase[Purchase::reference] like "P-%")

session.mutate(statement).execute()
```

They carry the same warning `mutate(hql)` does: they go straight to the database, past everything the
session knows — no cascades, no `@PreRemove`, and entities already loaded keep the values they had.
Neither can join; that is JPA's rule for a bulk statement.

**The type argument on `set` is not optional.** JPA declares both `set(Path<Y>, X)` and
`set(Path<Y>, Expression<out Y>)`, and against an expression Kotlin finds them equally applicable and
picks neither. Naming `Y` picks the second.

**A bulk statement with nothing restricting it is a statement against the whole table**, and Criteria
will render it without comment. Where the restrictions are assembled from parts — `all(filters)`
answering `null` for a request that narrowed nothing — check the result before running it.

## HQL and native queries

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

## Below the session

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

## Reads and writes without a repository

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
scroll, order by the identifier and resume from the last one seen. A `limit` above `MAX_PAGE_SIZE`
(1000, from `stx-common`) is refused, as it is in `stx-mongo`. A query that fetches a collection is refused too, at any depth,
because the limit would cut the joined rows rather than the owners; see [Fetch joins, and the N+1](#fetch-joins-and-the-n1).

**Writing is stateful-session only.** A stateless session has no persistence context, so `update`
would have nothing to merge into and `delete` nothing to cascade from. The reads take the narrower
`JpaQueries`, so a stateless session gets them too.

---

Underneath it is JPA Criteria: Hibernate renders the SQL, and this is a Kotlin surface over its query
model rather than a second implementation of HQL that would have to learn every dialect's quoting.
When a terminal has to name the query in an exception it renders the tree back to HQL, and only then.
