# What a shared-jpa query may say

The vocabulary `com.strange.jpa.criteria` adds to JPA's Criteria API — an operator, a function, an
escape. This is the half of `libs/shared-jpa` that gains an entry every phase, so it lives here
rather than in the module README, which answers *why the library is shaped this way* and stays
roughly the size it is.

`libs/shared-jpa/README.md` has the rest: the confinement rule, the four ways in, and what a query
says it loads. `docs/jpa-mapping.md` is the other half of this one — what an *entity* may say.

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
- **`limit` and `offset` do not compose with a `fetchEach`, and nothing refuses the combination.**
  The database applies them to the joined rows, not to the owners: measured, `limit(2)` over three
  purchases holding three, one and no lines answers with *one* purchase holding *two* of its three
  lines — fewer owners than asked for, one of them silently incomplete and cached as whole. Page the
  owners first and fetch their collections in a second query off the ids, or project the columns the
  page actually shows. A to-one `fetch` multiplies no rows and is left alone.
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

- **Loading by identifier.** `find`, `get` and `JpaRepository.findById`/`requireById` have no query
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

---

Underneath it is JPA Criteria: Hibernate renders the SQL, and this is a Kotlin surface over its query
model rather than a second implementation of HQL that would have to learn every dialect's quoting.
When a terminal has to name the query in an exception it renders the tree back to HQL, and only then.
