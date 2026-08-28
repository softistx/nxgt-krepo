# The shared-jpa query DSL

What a query may say, when it is written from the entity's own properties rather than as a string.
This is the half of `libs/shared-jpa` that gains an entry every phase — an operator, a function, an
escape — so it lives here rather than in the module README, which answers *why the library is shaped
this way* and stays roughly the size it is.

`libs/shared-jpa/README.md` has the rest: the confinement rule, the four ways in, pagination, where
this DSL deliberately stops and Criteria takes over, and the repository and service above it.
`docs/jpa-mapping.md` is the other half of this one — what an *entity* may say.


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
silently ignored.

**`distinct()` is about the SQL, not about the rows you get back.** A `joinEach` does duplicate the
owner once per element in the result set — but an entity query de-duplicates by identity before it
answers, on Hibernate 7, so `select<Purchase>` over a to-many join gives each purchase once with or
without it. A *projection* over the same join sees every row: three purchases holding three, one and
no lines are three entities and five projected rows. `FetchJoinTest` measures both. So reach for
`distinct()` on a projection, and know that it changes the query the database runs rather than the
list Kotlin receives.

## Fetch joins, and the N+1

```kotlin
session
    .select<Purchase> {
        fetch(Purchase::customer)        // to-one
        fetchEach(Purchase::lines)       // to-many, whole
    }.list()
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
  filter; a fetch is about loading, and an inner fetch would quietly drop every owner with no
  children. Pass `JoinType.INNER` to mean it.
- **What comes back is a `JoinScope`**, because Hibernate's fetch node is a join too — so a fetched
  association filters, orders and indexes like any other, and the query does not join twice to do
  both. Filtering *through* a fetched collection is the one trap: the surviving rows become the whole
  collection as far as the persistence context knows, so it reads back incomplete and says nothing.
  Take a separate `joinEach` when both are wanted.
- **`distinct()` is not needed after a `fetchEach`**, per the paragraph above.
- **`limit`, `offset` and `page` are refused after a `fetchEach`.** The database applies them to the
  joined rows, not to the owners: measured, `limit(2)` over three purchases holding three, one and no
  lines answers with *one* purchase holding *two* of its three lines — fewer owners than asked for,
  one of them silently incomplete and cached as whole. Page the owners first and fetch their
  collections in a second query off the ids, or project the columns the page actually shows. A
  to-one `fetch` does not multiply rows and is left alone.
- **A `fetch` after a plain `join` on the same attribute is refused.** Hibernate has `isFetched` and
  `clearFetched` and no way to set it, so a join already taken cannot become a fetch, and asking for
  both emits two joins to the same table. Fetch first; the `join` after it gives back the same one.
- **Only `select` has them.** A projection has no owner in its select list to hang a fetch on, so
  Hibernate refuses one at execution — and a method that is always a runtime failure is better not
  offered.
- **One level.** There is no fetch from a fetch; two levels is a criteria query run through the same
  terminals, which is the same boundary subqueries and window functions sit behind.

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

**Above four columns, write every column as a path** — `this[Purchase::reference]` rather than
`Purchase::reference`. A fifth column mixing the two forms is an overload-resolution error listing
all 29 candidates, and this is the sentence that answers it. The cliff is arithmetic rather than an
omission: a mixture over n columns is 2ⁿ − 1 declarations, so four cost 15 and reaching seven the
same way would cost 221 more. Four is where the sugar still pays, and the uniform path form covers
every arity with one rule.

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
