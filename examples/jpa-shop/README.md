# jpa-shop

A shop catalogue over Postgres — the smallest thing that shows `shared-jpa` end to end, including
what a repository and a service look like when they are written rather than inherited.

```
POSTGRES_URI=postgresql://localhost:5432/shop \
POSTGRES_USER=… POSTGRES_PASSWORD=… \
./kotlin run -m jpa-shop
```

```
GET    /products?search=anvil&under=1000&first=20&skip=0
GET    /products/summary?first=20
GET    /products/{id}
POST   /products                  X-Acting-As: ada
PATCH  /products/{id}             X-Acting-As: ada
DELETE /products/{id}
```

## What each file is here to show

| | |
| --- | --- |
| `domain/Product.kt` | An ordinary annotated Kotlin class extending `AuditedEntity`, so the four audit columns come with it |
| `domain/ProductCatalogue.kt` | Named filters that are just `Predicate`s, and a repository built from `createQuery`, `[]`, `eq` and `all` |
| `domain/ProductService.kt` | Create, update and delete written out — about forty lines, every one of them about products |
| `routes/ProductRoutes.kt` | Read in `session { }`, write in `transaction { }`, and filters composed from a query string |
| `ShopServer.kt` | `install(JpaConnection)` with `packages(…)` — one factory for the application, closed with it, mapping found by scanning |

## The five things worth reading it for

**A named restriction is a function returning a `Predicate`.** `ProductFilters.available(product)` is
the whole idea — no specification type, no framework, and `all(filters)` folds however many of them a
request turned out to want into one. A filter list that narrowed nothing answers `null`, which is
what an unrestricted request should mean and what the repository checks for.

**The mapping is scanned, not listed.** `packages("com.strange.example.shop.domain")` maps every
annotated class in the package, so `ShopServer` never mentions `Product` and the next entity is mapped
by having been written. The cost is the other direction: a class that moves out of the package stops
being mapped and nothing fails to compile — which is why `ShopTest` asserts the package string finds
`Product` rather than trusting it. A scan that finds no entity at all fails the install.

**The session is an argument, never a field.** A session belongs to the event loop that opened it and
does not outlive its block, so the repository is a singleton and the unit of work arrives per call.
That is what lets a route put a search and its count in one session, and two writes in one
transaction.

**An update assigns fields; Hibernate decides what that is worth writing.** `ProductService.update`
leaves alone what the request left null, and a field assigned the value it already had produces no
SQL and moves no audit timestamp.

**A write outside a transaction writes nothing, and nothing checks it for you.** `session { }`
flushes nothing, so `create` there would answer with a product and store no row. Which of the two a
handler opens is the handler's decision — `ProductRoutes` is four lines where that decision is made,
and the reason `session`-for-reads and `transaction`-for-writes is a rule rather than a habit.

**`/products/summary` loads no entity at all.** `query<ProductSummary>("select sku, name, …")` hands
Hibernate a result class and it packages the three columns into it — the way to read part of an
entity, and the one that has no association to fetch.

## Tests

`test/ShopTest.kt` covers what this module owns and can check without a database: that the package
`ShopServer` scans really does contain the entity, and that the payloads decode and map as the routes
assume.

The library's behaviour is covered where it lives — `libs/shared-jpa` has integration specs against a
real Postgres, each in a schema of its own. Repeating that here would mean a second container or
writing into the workspace server, and neither belongs in an example.
