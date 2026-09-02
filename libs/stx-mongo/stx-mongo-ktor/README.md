# stx-mongo-ktor

`install(MongoDB)` — one Mongo client for the application, one database handle over it, both closed
when it stops.

```kotlin
install(MongoDB) { uri = System.getenv("MONGO_URI"); database = "orders" }

get("/orders/{id}") { call.respond(call.database.collection<Order>("orders").findById(id)) }
```

## Why the client is built in `stx-mongo` and not here

The plugin calls `mongoClient(uri, configure)`, which lives in the library. That is deliberate, and
it is the reason this module is thin: a client built **without** the codec registry stores an
`Instant` as something nothing in `stx-mongo` can read back, and every step succeeds until the data
is already written. That is a fact about the driver, not about the web framework — a worker or a CLI
needs the same client and should not have to install a Ktor plugin to get one built correctly.

So this module owns the *lifecycle*, not the construction.

## Two handles, and why both

`call.mongo` is the client; `call.database` is the one database the plugin was configured with, and
it is what a route almost always wants. The client is still exposed because a second database and a
causally-consistent session are both asked of the client rather than of a database.

The driver's client is a pool and is thread-safe, so one per application is right. `configure` is
there for the TLS, pool and read-concern settings a deployment has opinions about and this module
should not.

## `uri` and `database` have no defaults

Both are required, and neither gets a fallback: a default connection string is a guess about
somebody's cluster, and a default database name is a guess about what is in it. `instance` is the
escape — a client built by a DI container or by hand, which this plugin then does **not** close,
because whoever created it closes it.

## Why this is a module and not a package in `stx-ktor`

It used to be `com.softistx.ktor.mongo`, one of seven integrations in one artifact. Publishing it
beside its library is what the other families here already do, and it lets the `stx-mongo`
dependency be `exported` instead of `compile-only` — in the hub it had to be optional because six
other integrations were; here it is what the module is made of.

`own`, `publish`, `resource` and `required` stay in `stx-ktor`: they belong to no integration, and
this module is a caller of them.
