# stx-redis-ktor

`install(RedisConnection)` — one namespaced Redis connection for the application, closed when it
stops.

**`io.github.softistx:stx-redis-ktor`** — [how to depend on it](../../../../docs/consuming.md).

```kotlin
install(RedisConnection) { config = RedisConfig(uri = System.getenv("REDIS_URI"), namespace = "orders") }

get("/cart/{id}") { call.respondText(call.redis.commands.get(call.redis.key("cart", id)) ?: "") }
```

## Why exactly one

A connection here is a pool, and a pool is the thing you want one of. One per request spends a round
trip on every call; one per route leaves as many as there are routes. Closing it is the half that
gets forgotten, and it costs nothing visible until a redeploy loop has left a server holding
connections nobody is on the other end of.

So `call.redis` is shared, deliberately. That is safe because a `Redis` is a pool with a namespace,
not a session.

## `config` is defaulted where `MongoDB`'s `uri` is not

`RedisConfig()` is already the type that carries this library's defaults, so the plugin defaults to
it rather than requiring one. A second opinion about those defaults here would only be a place for
the two to disagree. `MongoDB` has no equivalent — a connection string and a database name are
guesses about somebody's cluster, so that plugin requires both.

## Injection, and the double close

Installing the plugin registers the connection with Ktor's DI. It is not a flag: the connection the
plugin already opened goes into the container rather than letting it build a second one, so a class
the container builds takes a `Redis` in its constructor and gets the one routes are using. The
container then closes it at application stop — *as well as* the plugin — and that is fine:
`Redis.close` goes through `CloseGuard`.

The two facts this rests on are pinned by specs rather than taken from documentation. Ktor's
container closing every `AutoCloseable` it hands out, including one a provider merely passed
through, and a per-key `cleanup` running *beside* that rather than instead of it, are checked
without any backend in `stx-ktor`'s `DependenciesTest`. `RedisDependenciesTest` here is the half
that needs a real server: that routes and the container get the same connection, and that closing it
twice is harmless.

A connection that has to outlive the application does not belong in the container — use `instance`,
which the plugin adopts and does not close.

## Why this is a module and not a package in `stx-ktor`

It used to be `com.softistx.ktor.redis`, one of seven integrations in one artifact. Publishing it
beside its library is what the other families here already do, and it lets the `stx-redis`
dependency be `exported` instead of `compile-only`.

`own`, `publish`, `resource` and `required` stay in `stx-ktor`: they belong to no integration.

---

Apache-2.0 · [Contributing](../../../../CONTRIBUTING.md) · [All the libraries](../../../../README.md)
