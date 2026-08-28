# jpa-shop

A shop catalogue over Postgres — the smallest thing that shows `shared-jpa`'s repository and service
layer end to end.

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
| `domain/ProductRepository.kt` | `JpaRepository<Product, Long>(Product::id)` — nothing names the class — plus named `JpaSpec`s and the two finders that are actually about products |
| `domain/ProductService.kt` | `buildCreate` and `applyUpdate`, the only two methods the flow cannot write for you |
| `routes/ProductRoutes.kt` | Read in `session { }`, write in `transaction { }`, and specifications composed from a query string |
| `ShopServer.kt` | `install(JpaConnection)` with `packages(…)` — one factory for the application, closed with it, mapping found by scanning |

## The five things worth reading it for

**Nothing names the entity class.** `JpaRepository<Product, Long>(Product::id)` is the whole
declaration: a property reference already knows whose it is, and a class cannot have a `reified` type
parameter, so this is how the base class learns what it is generic over.

**A named restriction is a `JpaSpec`, which is a function returning a `Predicate?`.**
`ProductSpecs.available` is the whole idea — no specification interface to implement — and `and`
composes however many of them the query string asked for. One answering `null` restricts nothing,
which is what an unrequested filter should mean.

**The mapping is scanned, not listed.** `packages("com.strange.example.shop.domain")` maps every
annotated class in the package, so `ShopServer` never mentions `Product` and the next entity is mapped
by having been written. The cost is the other direction: a class that moves out of the package stops
being mapped and nothing fails to compile — which is why `ShopTest` asserts the package string finds
`Product` rather than trusting it. A scan that finds no entity at all fails the install.

**The session is an argument, never a field.** A session belongs to the event loop that opened it and
does not outlive its block, so the repository is a singleton and the unit of work arrives per call.
That is what lets a route put a search and its count in one session, and two writes in one
transaction.

**An update assigns fields; Hibernate decides what that is worth writing.** `applyUpdate` leaves
alone what the request left null, and a field assigned the value it already had produces no SQL and
moves no audit timestamp — no hook compares anything.

**A write outside a transaction is refused.** `session { }` flushes nothing, so `create` there would
answer with a product and store no row. The repository throws `JpaOutsideTransactionException`
instead, naming the operation and the entity, which is what makes `session`-for-reads and
`transaction`-for-writes a rule rather than a habit.

**`/products/summary` loads no entity at all.** `query<ProductSummary>("select sku, name, …")` hands
Hibernate a result class and it packages the three columns into it — the way to read part of an
entity, and the one that has no association to fetch.

## Tests

`test/ShopTest.kt` covers what this module owns and can check without a database: that the repository
resolves its entity from a property reference *from a consuming module*, that the package
`ShopServer` scans really does contain the entity, and that the payloads decode and map as the routes
assume.

The library's behaviour is covered where it lives — `libs/shared-jpa` has integration specs against a
real Postgres, each in a schema of its own. Repeating that here would mean a second container or
writing into the workspace server, and neither belongs in an example.
