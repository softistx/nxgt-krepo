# stx-migrations-ktor

`install(Migrations)`, and the server does not bind until they pass.

```kotlin
install(JpaConnection) { config = JpaConfig(uri = …, username = …, password = …) }

install(Migrations) {
    sql(application.jpa) {
        migration(V1Orders(), V2OrderIndex())
    }
}

get("/health/migrations") { call.respond(call.migrations.map { "${it.version} ${it.status}" }) }
```

## Why `runBlocking` inside `install` is the gate

`MigrationRunner.run()` suspends and plugin installation does not, so this is where `runBlocking` is
called — at startup, on the thread starting the application, before anything is serving. That is the
same argument `install(JpaConnection)` already makes about building a session factory, and here it is
load-bearing rather than tidy: an exception leaves the install block, leaves `embeddedServer`, and the
port is never opened.

The failure this replaces is concrete. The runner in `stx-spring-boot` was a suspending
`@EventListener(ApplicationReadyEvent)`, and `SuspendingListenerTest` pins that **Spring does not wait
for a suspending listener** — the port opened while migrations were still running, and the example
application worked around it by polling the ledger from its own specs. `MigrationPluginTest` asserts
the other way round: `migrated` before `served`, in that order, and a failing migration reaching no
route at all.

## Install it after the connection plugin it reads from

A runner is built from a `Jpa` or a `MongoDatabase` that another plugin put on the application, and
`application.jpa` throws by name when that install has not happened. Ktor runs install blocks in
order, so **the ordering is the whole mechanism** — there is nothing here to configure and nothing
that would notice the mistake for you.

More than one `gate(…)` is allowed, and they run in the order added. An application migrating a SQL
database and a MongoDB adds two. They are separate ledgers with separate locks, so *in order* is a
statement about this process and not a transaction across two servers.

## It owns nothing and closes nothing

A runner holds a ledger, a ledger holds a connection somebody else opened, and none of the three is
`AutoCloseable` — so this plugin uses `publish` and never `own`. What it puts on the application is
the `List<MigrationRecord>` it read, for a health route; the same list goes into Ktor's DI, so a
class the container builds can report on it too. Neither is a flag.

`call.migrations` is a **snapshot**, not a live read. Once the gate has passed, the ledger only
changes when another process migrates — and a route re-reading it every request would be asking a
database a question this process cannot act on anyway.

## The list of migrations is explicit

There is no bean registry to ask here, and no scan should pretend to be one. `stx-jpa`'s
`scanEntities` already states the position: *"a list breaks the build when a class moves; a scan finds
nothing and starts perfectly, and the first query is where you learn about it."* For migrations that
second failure is exactly what this library exists to prevent. A migration's constructor usually takes
nothing, so the list is one line.

A ClassGraph scan is deferred rather than designed out. Note for whoever writes it: finding subtypes
needs `enableClassInfo()` and `getClassesImplementing`, a different switch from `EntityScan`'s
`enableAnnotationInfo()`, and there is no precedent for it in this repo.

## `gate` is the contract; `sql { }` and `mongo { }` are sugar over it

`MigrationsConfiguration.gate` takes a `MigrationRunner<*>` the application has already built, and
that is the whole of what the plugin runs. The DSL in `Stores.kt` builds one of the two stock runners
and calls `gate` with it — so the two mix in a single block, and an application with a ledger of its
own is not pushed onto a legacy path:

```kotlin
install(Migrations) {
    sql(application.jpa) { migration(V1Orders()) }
    mongo(application.database) { migration(V1Seed(), V2Tags()) }
    gate(myOwnRunner)
}
```

Both take the connection explicitly. That is the one thing about a Ktor application the plugin could
not otherwise know — there is no bean registry here — and guessing it is how migrations end up
running against the wrong database. `application.database` and not `application.mongo`: the latter is
the client, and a ledger is written in one database.

**`Stores.kt` is a file of its own, and `Plugin.kt` still names no store.** `stx-migrations-db`,
`stx-jpa` and `stx-mongo` are `compile-only` here — the three lines `stx-migrations-spring` already
carries, for its reason: a ledger cannot be *constructed* without the library that provides its
connection, so an application calling `sql { }` already depends on `stx-jpa`, and one that calls
neither loads a class from none of them. Verified with `./kotlin show dependencies -m
stx-migrations-ktor`: all three sit in COMPILE and are absent from RUNTIME.

That is also why the specs here need no container. `InMemoryLedger` from the core is a real ledger
with a real conditional claim and a real lock, so `MigrationPluginTest` runs in about a second; and
`StoresTest` builds a real `MongoMigrationLedger` over a database handle pointed at an unreachable
URI, because the driver's client connects lazily and assembling a ledger contacts nothing. Whether
that ledger works against a server is `stx-migrations-db`'s question, asked there against three.

`sql { }` has no twin in `StoresTest`, on purpose: `Jpa` has an `internal` constructor, so reaching
one means starting Hibernate against a real database, and what that would assert is six lines of
pass-through whose mirror image is already asserted. The part with logic is the collector, and the
two builders share it.
