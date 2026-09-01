# stx-migrations-ktor

`install(Migrations)`, and the server does not bind until they pass.

```kotlin
install(JpaConnection) { config = JpaConfig(uri = System.getenv("POSTGRES_URI"), username = …, password = …) }
install(Migrations) {
    gate(SqlMigrations(application.jpa, listOf(V1Orders(), V2OrderIndex())))
}

get("/health/migrations") {
    call.respond(call.migrations.map { "${it.version} ${it.description} ${it.status}" })
}
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
the `List<MigrationRecord>` it read, for a health route; `injectable = true` puts the same list in
Ktor's DI, and `ktor-server-di` is compile-only here so an application that never asks for it never
loads a class from it.

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

## This module names no store

It depends on the **core** only. `MigrationsConfiguration.gate` takes a `MigrationRunner<*>` the
application has already built — exactly as `stx-workflow-ktor` takes a `WorkflowStore` and never
names Redis. Which store is underneath is `stx-migrations-db`'s question.

Which is also why the specs here need no container: `InMemoryLedger` from the core is a real ledger
with a real conditional claim and a real lock, so `MigrationPluginTest` runs in about a second.
