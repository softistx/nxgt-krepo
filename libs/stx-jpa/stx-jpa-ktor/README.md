# stx-jpa-ktor

`install(JpaConnection)` — one Hibernate Reactive session factory for the application, built when it
starts and closed when it stops.

```kotlin
install(JpaConnection) {
    config = JpaConfig(uri = System.getenv("POSTGRES_URI"), username = …, password = …)
    packages("com.acme.orders.domain")   // or entities(Order::class, Customer::class)
}

get("/orders/{id}") {
    call.respond(call.jpa.transaction { it.get<Order>(call.parameters["id"]!!.toLong()) })
}
```

Three files, and the split is the point: `Plugin.kt` builds the factory, `Calls.kt` is how a route
reaches it, and `Dependencies.kt` is the one thing that touches `ktor-server-di`. Installing the
plugin calls it — the factory goes into Ktor's container as well as onto the application, so a class
the container builds takes a `Jpa` in its constructor and gets the one routes are using. There is no
flag for that, and no second factory anywhere.

## Why a factory and not a session

A factory is expensive to build and cheap to share; a session is neither shared nor long-lived. A
route opens one for the length of a `transaction { }` block and it goes away with the block. So the
plugin owns exactly one thing, and `call.jpa` hands out the same instance to every request.

That sharing is safe and the confinement rule is not a detail: a session belongs to the block that
opened it **and to the Vert.x context it runs on**. It must not be stored, handed to another
coroutine, or awaited outside the block. `libs/stx-jpa/stx-jpa/README.md` has the reasoning.

## Why `runBlocking` inside `install`

`Jpa.connect` suspends and plugin installation does not, so the bootstrap blocks — at startup, on
the thread starting the application, before anything is serving. The alternative is a server
accepting requests while its mapping metadata is still being built. `stx-migrations-ktor` makes the
same call for the same reason, one step further along.

**Nothing connects at install.** The pool opens its first connection when a route asks for a
session, so a wrong password is a failed request rather than a failed startup. `SchemaMode.VALIDATE`
turns that back into a startup failure when the schema is managed elsewhere — which, in this repo,
is what `stx-migrations` is for.

## A scan that finds nothing fails the install

`packages("com.acme.orders.domain")` reads entities off the classpath instead of naming them, and a
scan finding **no** entity fails rather than starting a server that maps nothing. That is the whole
argument for a list over a scan, kept as a spec: an entity missing from `entities` and not found by
`packages` is not a startup error, it is an `IllegalArgumentException` on the first query naming it.

## Why this is a module and not a package in `stx-ktor`

It used to be `com.softistx.ktor.jpa`, one of seven integrations in one artifact. Publishing it
beside its library is what the other four families here already do — `stx-workflow-ktor`,
`stx-migrations-ktor`, `stx-graphix-ktor`, `stx-telemetry-ktor` — and it lets the `stx-jpa`
dependency be `exported` instead of `compile-only`. In the hub it had to be optional, because six
other integrations were; here it is what the module is made of.

`own`, `publish`, `resource` and `required` stay in `stx-ktor`: they belong to no integration, and
this module is a caller of them.
