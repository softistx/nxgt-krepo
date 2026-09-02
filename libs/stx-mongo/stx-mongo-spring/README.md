# stx-mongo-spring

One `stx-mongo` client for a Spring Boot application and one database over it, behind one property.

```yaml
stx:
  mongo: { enabled: true, uri: mongodb://localhost:27017, database: orders }
```

```kotlin
class OrderRepository(private val orders: MongoDatabase)   // injected like any other bean
```

## The client is built through the library, not assembled here

`mongoClient(uri, …)` in `stx-mongo` is what builds it, and that is the whole reason this
auto-configuration is thin. A client built **without** the codec registry compiles, connects and
reads — and stores an `Instant` as something the library cannot read back. Every step succeeds until
the data is already written, so the library does it once rather than each caller remembering to.

`stx-mongo-ktor` says the same thing from the Ktor side. Both own a lifecycle, neither owns the
construction.

## `uri` and `database` are required

Enabling it without a `uri` fails naming `stx.mongo.uri`, rather than letting the binder complain
about a constructor parameter — which is not where the reader has to look. Neither has a default: a
connection string is a guess about somebody's cluster and a database name a guess about what is in
it.

## An application's own client wins

`@ConditionalOnMissingBean`, so declaring a `MongoClient` bean takes over — and the database is
still built over **that** client rather than opening a second pool. That is how TLS, pool sizes and
read concerns get set: this module has no opinion about them and should not grow a property for each
one.

## Two configurations can contribute a `MongoDatabase`

`stx-spring-boot`'s `stx.data.mongo` bridges Spring Data's own pool to a coroutine `MongoDatabase`
so that an application already on Spring Data can hand that handle to `stx-migrations` without
opening a second client. This module contributes one too, from a client `stx.mongo` opened itself.

Both are `@ConditionalOnMissingBean`, and the loser of that race is a migration run against a
database nobody chose — so `MongoAutoConfiguration` there orders itself after this one with
`@AutoConfiguration(afterName = …)`. **A name and not a class reference**, because this module
depends on nothing in `stx-spring-boot` and the edge may not be reversed for the sake of an ordering
hint. `MongoDatabaseOrderingTest` over there pins the name and the outcome, and states what it
cannot pin.

## Why this is a module and not a package in `stx-spring-boot`

It used to be `com.softistx.spring.integration.mongo`, one of seven auto-configurations in the
Spring hub. Beside its own library it can be published and versioned on its own, and its `stx-mongo`
edge becomes `exported` rather than `compile-only`.
