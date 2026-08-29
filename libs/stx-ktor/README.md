# stx-ktor

Ktor integrations for the libraries here — one package per integration, one module for all of them.

```
com.strange.ktor.i18n      I18n              a negotiated locale per request
com.strange.ktor.redis     RedisConnection   one Redis connection
com.strange.ktor.mongo     MongoDB           one client, and the database over it
com.strange.ktor.amqp      AmqpConnection    one AMQP connection
com.strange.ktor.kafka     KafkaCluster      the cluster configuration
com.strange.ktor.storage   Storage           one object-storage client
com.strange.ktor.jpa       JpaConnection     one Hibernate Reactive session factory
```

```kotlin
install(RedisConnection) { config = RedisConfig(uri = System.getenv("REDIS_URI"), namespace = "orders") }
install(MongoDB) { uri = System.getenv("MONGO_URI"); database = "orders" }
install(I18n)  { messages = Messages.load(locales = listOf(ENGLISH, FRENCH)) }

get("/cart/{id}") {
    val cart = call.database.collection<Cart>("carts").findById(call.parameters["id"]!!)
    call.respondText(call.translate("cart.total", mapOf("total" to cart.total)))
}
```

## The names

No `Plugin` suffix, the way Ktor's own `install(ContentNegotiation)` carries none. Four of the seven
could not simply take their backend's name: `Redis`, `Amqp`, `Kafka` and `Jpa` are the classes these
plugins *hand out*, and an application that installs one and also names the type it gets back would
have two imports of one name. Those three are named for what the plugin puts on the application —
a connection, a connection, a cluster, a connection — and the other three take the product.

Each package is then two files with the same two names: `Plugin.kt` for the plugin and its
configuration, `Calls.kt` for the `Application.x` and `ApplicationCall.x` a route reaches through.
The package already says which backend it is, so the file names do not repeat it.

## What a plugin is for

**A connection is a pool, and a pool is the thing you want exactly one of.** One per request spends
a round trip on every call; one per route leaves as many as there are routes. So each plugin opens
one at startup and puts it on the application, and `call.<thing>` hands the same one to every route.

**Closing it is the half that gets forgotten.** Every plugin here subscribes the connection to
`ApplicationStopped` through the same helper, because five plugins each remembering separately is
four chances to leak a pool per redeploy — a mistake nothing fails on until a server runs out of
file handles. `ApplicationStopped` and not `ApplicationStopping`: stopping fires while requests may
still be in flight, and a request that finds its connection already closed is a 500 caused by the
shutdown rather than by the caller.

**A missing `install` names itself.** `call.redis` without `install(RedisConnection)` throws saying
exactly that, rather than surfacing as a null three layers down.

## Dependency injection

**`call.redis` is a service locator, and a class the container builds has no call.** So each plugin
can register what it installed:

```kotlin
install(RedisConnection) { config = RedisConfig(uri = …); injectable = true }

class CartStore(private val redis: Redis)          // built by the container
fun Application.orders(redis: Redis) { … }         // injected by type
```

`injectable = true` is `provideRedis()`, which every package also exposes on its own for an
application that would rather write the two lines. Either way it registers **the connection the
plugin installed** — `call.redis` and an injected `Redis` are one connection, not two.

It is off by default because `ktor-server-di` is compile-only here like every backend, and each
`provideX` sits in its own file so that nothing loads a class from Ktor's DI until the flag is set.

**The other direction works too**, for a connection something else owns — a Koin module, a
`main` that built it, a test:

```kotlin
val redis: Redis by dependencies                   // `resolve` suspends; the delegate does not
install(RedisConnection) { instance = redis }
```

Then the plugin adopts it and never closes it. **Whoever created it closes it** — that is the whole
rule, and `Resources.kt` is where it is written: `own` for what a plugin opened, `publish` for what
it was handed.

**What the container does with it is not negotiable.** Ktor's DI closes every `AutoCloseable` it
hands out at application stop, including one a provider merely passed through, and a per-key
`cleanup` runs beside that hook rather than instead of it — `test/di/DependenciesTest.kt` pins both.
So `injectable = true` hands the container a second claim on closing the connection. That is safe
because these clients close through `CloseGuard`, and it is the reason a connection which has to
outlive the application should not be registered at all.

## The ones that are not like the others

**`KafkaCluster` opens nothing and closes nothing.** That is not an omission — it is what `Kafka`
itself says: a Kafka client connects when it is created, and a producer, a consumer and an admin
client have different lifetimes, threads and failure modes. A wrapper that owns them all is a
wrapper that closes a producer something else was still using. So the plugin holds the
configuration, `call.kafka` reaches it, and whatever a route opens, that route closes. A long-lived
publisher belongs to the application: open it at startup and close it on `ApplicationStopped`, the
way the other plugins do.

**`MongoDB` exists mostly for the codec registry.** A `MongoClient` built without
`mongoCodecRegistry()` compiles, connects and reads — and stores an `Instant` as something this
library cannot read back. Every step succeeds until the data is already written, so the plugin does
it rather than each service remembering to. A round trip through a real server is the only spec that
can tell the difference, and there is one.

**`JpaConnection` maps what it is told about.** `entities(Order::class, Customer::class)` is the
mapping — a class missing from it is not a startup error but an `IllegalArgumentException` on the
first query that names it. `packages("com.acme.orders.domain")` reads the classpath instead, and
fails the install when it finds no entity there, because a mistyped package is otherwise silent.
It blocks once at install
for the same reason `AmqpConnection` does, and it connects to nothing there: the pool opens its
first connection when a route asks for a session, which is why `SchemaMode.VALIDATE` is worth having
when the schema is managed elsewhere.

Its spec is the one worth reading in this module. A route persists, `delay`s, then reads back inside
one transaction — because suspending mid-transaction is the ordinary case for a handler, and it is
what kills a naive coroutine bridge over Hibernate Reactive. `stx-jpa`'s README has the rule.

**`AmqpConnection` blocks once, at startup.** `Amqp.connect` suspends and plugin installation does not,
so this is the module's one `runBlocking` — on the thread starting the application, before anything
is serving. The alternative is a server accepting requests while its broker connection is still
being made, and answering the first of them with what looks like the broker's fault.

## One module without a fat dependency list

Every backend is `compile-only`, **including the ones this module's API returns**. `call.redis`
hands back a `Redis` and Lettuce still stays off a consumer's runtime classpath.

That sounds wrong and is not. An application that installs `RedisConnection` already depends on
`stx-redis` — `RedisConfig` is the only way to configure the plugin at all — so the driver is on
its classpath for its own reasons. And an application that installs only `I18n` never loads a
class from any of the others, so nothing is missing when nothing is linked. The rule enforces itself
rather than asking anyone to remember it.

```
./kotlin show dependencies -m stx-ktor    # a compile-only entry: in COMPILE, absent from RUNTIME
```

The tests need the real thing at runtime, so `test-dependencies` carries each library again at
normal scope plus `//libs/stx-testing` for the servers. Every plugin is specced against a real
backend: "one connection, closed on stop" is not observable from a mock, and the closed-on-stop
assertion is the one the plugins exist for.

## i18n

The odd one in a good way — it resolves something per request rather than owning a connection.

```kotlin
install(I18n) { messages = Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH)) }

get("/greeting") { call.respondText(call.translate("hello.world", mapOf("name" to "Ada"))) }
```

The locale is negotiated at call setup and kept on the call. Negotiating inside each handler that
needs a message would parse the same header several times and — worse — could answer two questions
in one response in two different languages.

```kotlin
install(I18n) {
    messages = catalogs
    header = "X-Language"     // default: Accept-Language
    queryParameter = "lang"   // default: null, i.e. off
}
```

`header` exists for a service behind something that rewrites the client's own. `queryParameter` is
**off by default, deliberately**: `?lang=fr` is genuinely useful for a link somebody sends a
colleague, and it is also a second thing the same URL can mean, which every cache in front of the
service has to be told about. That is a decision to take, not a default to inherit. When it is on,
it beats the header — it is the more deliberate of the two.

## CORS

```kotlin
cors(CorsPolicy(origins = listOf("http://localhost:5173")))
```

The odd one out among the packages here: it wraps a *Ktor* plugin rather than one of these
libraries. It belongs anyway, for the reason the rest of this module exists — an application should
configure a policy once and not per framework. `CorsPolicy` lives in `stx-common`, and `stx-spring`
builds Spring's `CorsConfiguration` from the same type, so the two cannot drift on what a given
configuration means.

The policy is validated before the plugin is installed, so a wildcard origin with credentials fails
while the application is starting rather than on somebody's first preflight. Two of its fields have
no Ktor equivalent — `path`, because Ktor scopes a plugin by installing it on a route, and
`originPatterns`, because Ktor matches a host and a scheme rather than a glob. `CorsPolicy.ignoredByKtor`
names them rather than letting them be silently absent.

## Why the plugins are not in the libraries they wrap

So that `stx-i18n`, `stx-redis` and the rest stay free of Ktor. A worker, a CLI or a Kafka
consumer uses the same libraries and has no server in it, and a library that drags a web framework
behind it is one those callers cannot use. The library knows the backend, this module knows the
framework, and neither has to know both.
