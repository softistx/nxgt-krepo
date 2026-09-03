# stx-ktor

The Ktor foundation the plugins in this repo are built on. Two things live here, and both are here
because they belong to **no** integration:

```
com.softistx.ktor         own / publish / resource / required   the resource-lifecycle idiom
com.softistx.ktor.cors    cors(policy)                          one CORS policy, shared with Spring
```

Every `install(…)` this repo ships is a module beside its own library — `stx-jpa-ktor`,
`stx-mongo-ktor`, `stx-redis-ktor`, `stx-kafka-ktor`, `stx-amqp-ktor`, `stx-storage-ktor`,
`stx-i18n-ktor`, and `stx-workflow-ktor`, `stx-migrations-ktor`, `stx-graphix-ktor`,
`stx-telemetry-ktor` beside theirs. Each of them depends on this module and none of them is depended
on by it.

## The idiom: whoever created it closes it

That is the whole rule, and `Resources.kt` is where it is written.

| | |
| --- | --- |
| `own` | the plugin opened this; close it on `ApplicationStopped` |
| `publish` | the plugin was handed this, or built something that owns nothing; do not close it |
| `resource` | `own` when the config gave no `instance`, `publish` when it did — the two lines every plugin would otherwise repeat |
| `required` | read it back, and throw naming the plugin that was not installed |

**A connection is a pool, and a pool is the thing you want exactly one of.** One per request spends a
round trip on every call; one per route leaves as many as there are routes. So a plugin opens one at
startup, puts it on the application, and `call.<thing>` hands the same one to every route.

**Closing it is the half that gets forgotten**, which is why it is a helper rather than a line in
each plugin: eleven plugins each remembering separately is ten chances to leak a pool per redeploy,
a mistake nothing fails on until a server runs out of file handles.

`ApplicationStopped` and not `ApplicationStopping`: stopping fires while requests may still be in
flight, and a request that finds its connection already closed is a 500 caused by the shutdown
rather than by the caller.

**A missing `install` names itself.** `call.redis` without `install(RedisConnection)` throws saying
exactly that, rather than surfacing as a null three layers down. That message is `required`'s
second argument, and it is why the function takes one.

## What the container does is not negotiable

Every plugin registers what it installed with Ktor's DI, which hands the container a second claim on
closing the resource. That is unconditional — there is no flag to turn it off. Two facts
decide whether that is safe, and neither is taken from documentation — `test/di/DependenciesTest.kt`
pins both, with a `Probe` and no backend:

- Ktor's DI closes every `AutoCloseable` it hands out, **including one a provider merely passed
  through** rather than built.
- A per-key `cleanup` runs *beside* that hook, not instead of it. Nothing a library registers can
  opt out; only the application can, by replacing `onShutdown` in `install(DI)`.

So a plugin that registers what it opened is closed twice, and that is fine because these clients
close through `CloseGuard` in `stx-common`. It is also the reason a connection which has to outlive
the application should not be registered at all — use `instance` instead, which a plugin adopts and
never closes.

The half of that story which needs a real server — that routes and the container get the *same*
connection — is `stx-redis-ktor`'s `RedisDependenciesTest`, beside the plugin it is about.

## CORS

```kotlin
cors(CorsPolicy(origins = listOf("http://localhost:5173")))
```

The one thing here that wraps a *Ktor* plugin rather than one of these libraries, and it belongs
because an application should configure a policy once and not per framework. `CorsPolicy` lives in
`stx-common`, and `stx-spring-boot` builds Spring's `CorsConfiguration` from the same type, so the
two cannot drift on what a given configuration means.

The policy is validated before the plugin is installed, so a wildcard origin with credentials fails
while the application is starting rather than on somebody's first preflight. Two of its fields have
no Ktor equivalent — `path`, because Ktor scopes a plugin by installing it on a route, and
`originPatterns`, because Ktor matches a host and a scheme rather than a glob.
`CorsPolicy.ignoredByKtor` names them rather than letting them be silently absent.

## Why this module has no integration in it

It used to have seven, one package each, every backend `compile-only` so that a fat dependency list
never reached a consumer. That worked — `compile-only` publishes as Maven `provided`, which is not
transitive — but it made one artifact the home of seven unrelated things, and it broke the rule this
repo states elsewhere: *an integration with a design of its own is a module beside its library.*

Beside its library, each one can be published and versioned on its own, and its library edge becomes
`exported` rather than `compile-only` — because in a module that *is* the JPA integration, `stx-jpa`
is not optional. It only looked optional in a hub that also held six others.

What is left here is what genuinely belongs to no library, and this module's own specs show it:
there is no backend in any of them.
