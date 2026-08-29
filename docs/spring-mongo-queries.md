# What a stx-spring Mongo query may say

The vocabulary `com.strange.spring.data.mongo` adds to Spring Data Reactive Mongo — a predicate, a
filter operator, a page. This is the half of `libs/stx-spring` that gains an entry every phase, so it
lives here rather than in the module README, which answers *why the integration is shaped this way*
and stays roughly the size it is.

[`libs/stx-spring/README.md`](../libs/stx-spring/README.md) has the rest: the opt-in model, the
error and locale decisions, and the seven library integrations.
[`docs/spring-configuration.md`](spring-configuration.md) is the other half of this one — every
`stx.*` key an application may set.

**Everything here is an extension on a Spring Data or Spring type.** There is no query builder of
this module's own and nothing that has to be finished before it is worth anything. A `Criteria` is
Spring's; what these add is that its fields are named `Product::price` instead of `"price"`, that
the combinators build the operator they say they build, and that a query string reaching them
cannot say `$where`.

## The predicate DSL

```kotlin
val cheap = all(Product::stock gt 0, Product::price lte 50).query
val anywhere = any(Product::name containing "kettle", Product::sku eq "K-1").query
```

`all`, `any` and `none` build an explicit `$and`, `$or` and `$nor`, and `.query` wraps a `Criteria`
in the `Query` a template method wants.

**Use them rather than chaining `.and(...)`.** A chain builds one document key per field and quietly
loses the second predicate on a field named twice — `where("price").gt(5).and("price").lt(10)` is
`{price: {$lt: 10}}`, which is a wider result set than the caller asked for and looks like data.

**Every operator has a `KProperty` form, and that is the point.** A field named by a string is a name
nothing checks: rename the property and the query still compiles and matches nothing, which reads as
"no results" rather than as "broken query". The string forms remain for fields that have no property
to name them — a `Map` entry, a subdocument path, `_id`.

| Operator | Builds | Notes |
| --- | --- | --- |
| `field eq value` | `$eq` | `value` may be null, which matches documents where the field is null or missing |
| `field ne value` | `$ne` | |
| `field lt value` | `$lt` | |
| `field lte value` | `$lte` | |
| `field gt value` | `$gt` | |
| `field gte value` | `$gte` | |
| `field oneOf values` | `$in` | takes a `Collection`, deduplicated into a set |
| `field noneOf values` | `$nin` | |
| `field exists present` | `$exists` | |
| `field size count` | `$size` | array length, not string length |
| `field matching pattern` | `$regex` | takes a `java.util.regex.Pattern` |
| `field containing text` | `$regex` | substring, case-sensitive, **quoted** — see below |
| `field containingIgnoringCase text` | `$regex` | substring, case-insensitive, quoted |
| `field notMatching pattern` | `$not` + `$regex` | |
| `field notContaining text` | `$not` + `$regex` | |
| `field notContainingIgnoringCase text` | `$not` + `$regex` | |
| `field near point` | `$near` | takes a Spring `Point` |
| `field within circle` | `$geoWithin` | takes a Spring `Circle` |

Each row has both forms: `Product::price gt 50` and `"price" gt 50`.

### Two rules the regex operators encode

**A substring search quotes what it was given.** `containing` wraps the text in `Pattern.quote`
before it becomes a regex. Without it a search box is a way to hand the database a regular
expression, and `(a+)+$` against a long field is a request that does not come back. It does not take
a hostile user to hit this — only someone searching for `C++`.

**The negated forms are real operators, not a negation applied afterwards.** They are
`where(f).not().regex(...)`, because Spring's `Criteria.not()` sets a flag that the *next* operator
consumes: `(field containing text).not()` negates nothing at all and returns the same documents as
the un-negated form. The version this was ported from had `!like` behaving exactly like `like`,
which is a filter that means the opposite of what it says.

## The filter grammar

What a `?filter=` query parameter may ask for, so a client can narrow a list without an endpoint per
combination.

```
?filter=status:eq:PAID;total:gte:100
?filter=or@email:eq:a@b.c;email:eq:d@e.f
```

Clauses are `field:operator:value`, separated by `;`, combined with `and` — unless the whole
parameter starts with `or@`, which combines them with `or` instead. There is no nesting and no
precedence: a grammar with parentheses is a parser, and a client that needs one wants an endpoint.

The field must match `[a-zA-Z_][a-zA-Z0-9_.]*` — dots allowed, for a subdocument path.

| Token | Becomes | Value is read as |
| --- | --- | --- |
| `eq` | `eq` | the raw string |
| `ne` | `ne` | the raw string |
| `lt` `lte` `gt` `gte` | the matching operator | `Long`, else `Double`, else `Instant`, else the string |
| `before` | `lt` | an ISO-8601 `Instant` |
| `to` | `lte` | an ISO-8601 `Instant` |
| `after` | `gt` | an ISO-8601 `Instant` |
| `from` | `gte` | an ISO-8601 `Instant` |
| `like` | `containing` | the raw string, quoted |
| `!like` | `notContaining` | the raw string, quoted |
| `ilike` | `containingIgnoringCase` | the raw string, quoted |
| `!ilike` | `notContainingIgnoringCase` | the raw string, quoted |
| `in` | `oneOf` | comma-separated |
| `!in` | `noneOf` | comma-separated |
| `exists` | `exists` | exactly `true` or `false` |
| `size` | `size` | an `Int` |
| `near` | `near` | `x,y` |
| `within` | `within` | `x,y,radius` |

**The set is closed, and that is a security property rather than a simplification.** A grammar that
passed its operator through to the driver would let a caller write `$where`, which is JavaScript the
server runs.

**A clause that does not parse is a 400 — it is never skipped.** This is the one place where lenience
is the wrong instinct, and it is deliberately the opposite of what `?sort=` does. Dropping a filter
clause returns *more* rows than the caller asked for, so `status:eq:PIAD` would answer with the whole
collection rather than with an empty page. Sorting can afford to shrug; narrowing cannot.

For the same reason **a comparison against unparseable text fails rather than coercing**. The version
this came from coerced anything unreadable to `0.0`, so `price:gte:cheap` quietly became `price >= 0`
and matched everything. `exists:yes` fails for the same reason — `toBoolean()` answers false for
`"yes"`, `"1"` and every typo, so a caller asking for documents that *have* a field would have been
given the ones that do not.

The failure arrives as `ApiException.badRequest("filters.invalid", mapOf("filter" to clause))`, so
the response names the clause it could not read, translated.

## The sort grammar

```
?sort=createdAt:DESC,name:ASC
```

`request.sort` returns a `List<SortOrder>` — not Spring Data's `Sort`, because spring-data-commons
has no business on the classpath of a consumer that only wanted to read a query parameter. The Mongo
package's `List<SortOrder>.toSort()` translates it.

**A clause has to match end to end**, against `([a-zA-Z_][a-zA-Z0-9_.]*):(ASC|DESC)` case-insensitively
on the direction. The property reaches a query as a field name, so a scanning parser is a way to
sort by something the caller did not name: `$where:ASC` *contains* a legal clause, and a parser that
searched rather than matched sorted by `where`.

Unlike a filter, **an unreadable sort clause is dropped**. Wrong order is a worse answer than the
caller wanted; wrong *rows* is a different answer entirely.

## Paging

```kotlin
val page = template.findPage<Order>(request.mongoPage())
```

`mongoPage()` reads `?filter=`, `?sort=`, `?size=` and `?cursor=` and puts each where it belongs.
Building the window by hand is fine too — `MongoPage.first(20, query = filter, sort = ordering)` —
but the ordering goes in `sort`, never on the query.

`MongoPage` is a `PageWindow` — `first`/`last`/`cursor`, the same interface `stx-mongo` and `stx-jpa`
implement, validated in `init` so a contradictory window fails where it was written. What is specific
here is the Spring Data `Query` and `Sort` it carries.

| Window | Means |
| --- | --- |
| `MongoPage.first(n, query, sort)` | the first `n`, forward |
| `MongoPage.first(n, cursor = c, …)` | the `n` after cursor `c` |
| `MongoPage.last(n, …)` | the last `n`, backward |

**Keyset, not `skip`.** An offset page re-reads every row it skips, so page 500 costs five hundred
pages of work — and a row inserted while a client is paging shifts every later page by one, so the
client sees a row twice or never sees it. A cursor resumes from a key: constant cost, stable under
writes.

Three rules the implementation enforces, each of which is silent when it goes wrong:

- **The ordering always ends in `_id`.** A keyset resumes from the last row's key, so the key has to
  be unique. Order by `name` alone and every document sharing a name is a coin toss between being
  served twice and being skipped.
- **Cursors carry stored field names, not property names.** A property annotated `@Field("t")` is `t`
  in the document, so a cursor built from the property name reads nothing back and every page after
  the first comes up empty. It is also why documents are fetched raw and decoded here rather than
  mapped by the template: a mapped object no longer has the stored values the cursor needs.
- **A cursor from a differently sorted query is refused.** It would page along the wrong key and
  answer with rows that look perfectly plausible.
- **The ordering must arrive as `sort`, not on the query.** `Query.with` appends, so a sort baked
  into the query *does* order the rows — while the cursor, built from the window's `sort`, carries
  `_id` alone. The first page is right, the second is empty, and the rest of the collection is
  unreachable. `MongoPage` refuses a pre-sorted query for this reason; it was a real defect, and
  this page's own example demonstrated it.

Every failure is an `ApiException` — a contradictory window, a page size of zero and a foreign cursor
are all a client sending something it should not have, so they are 400s under `pagination.invalid`
and they arrive translated.

## Reading a request

```kotlin
val page = request.mongoPage()          // ?filter= ?sort= ?size= ?cursor= — what a paged route wants
val query = request.mongoQuery          // ?filter= and ?sort=, for `find`; never for a page window
val filter = request.mongoFilter        // ?filter= alone
```

`mongoPage()` is the one a paged route wants; `mongoQuery` is for a `find` that returns everything
matching. `page`, `size`, `cursor`, `sort`, `requiredParam`, `listParam` and `body()` are in `web/`
and documented in the module README — they have no Mongo in them.

## The converters

`stxMongoConversions()` registers `Instant` ⇄ `Date` both ways, and `stx.data.mongo.enabled`
contributes it to Spring Data automatically.

**Without it a `kotlin.time.Instant` field fails at *query* time, not at mapping time**, with
`Can't find a codec` — so an entity that saved cleanly stops being readable. BSON has one date type
and the driver has a codec for `java.util.Date` and none for `kotlin.time.Instant`.

Note that BSON dates hold milliseconds: a round trip loses anything finer. That matters for a cursor
built from a timestamp and not at all for a `createdAt` somebody displays. Storing a string would
keep the nanoseconds and lose range queries and index ordering, which is the worse trade.

## Adding to this vocabulary

A new operator is one `infix fun` on `String`, one on `KProperty<*>`, one row in the table above, and
a spec. A new filter token is an `Operator` entry, a branch in `Filters.kt`, a row in the filter
table, and a spec that pins what an unparseable value does — that last one is the part that has been
wrong before.
