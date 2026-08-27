# shared-ktor

Ktor integrations for the shared libraries — one package per integration, one module for all of
them.

```
com.strange.ktor.i18n     the Accept-Language plugin over shared-i18n
```

The Redis, Mongo, Kafka and AMQP plugins go here as they appear, as siblings of `i18n`, not as new
modules: an application wires them together in one `install` block and should read them from one
dependency.

## Keeping one module from becoming a fat one

The obvious risk is the dependency list: an app that installs only the i18n plugin should not
inherit Lettuce, the Mongo driver, the Kafka clients and the RabbitMQ client because they happen to
live in the same jar.

The answer is the scope. **A backend's library goes in `compile-only` unless the plugin's own public
API exposes it.** The plugin compiles against it; nothing downstream gets it at runtime. That is
sound because an app installing the Redis plugin already depends on `shared-redis` — it is using
Redis — so the driver is on its classpath for its own reasons, not this module's.

```
./kotlin show dependencies -m shared-ktor    # a compile-only entry: in COMPILE, absent from RUNTIME
```

The tests are the other half. Integration specs gate on server availability the way the rest of the
repo does — `feature("…").config(enabled = RedisTestServer.available)` — so `./kotlin test -m
shared-ktor` stays green on a machine with nothing running, and exercises what *is* running.

## i18n

```kotlin
install(I18n) { messages = Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH)) }

get("/greeting") {
    call.respondText(call.translate("hello.world", mapOf("name" to "Ada")))
}
```

That is the whole API, plus `call.translator` when a handler wants the
[`Translator`](../shared-i18n/README.md) itself.

**Resolved once per call.** The locale is negotiated at call setup and kept on the call.
Negotiating inside each handler that needs a message would parse the same header several times
and — worse — could answer two questions in one response in two different languages.

`call.translator` throws when the plugin is not installed, rather than quietly answering in English.
A service whose translations silently stopped negotiating is worse off than one that fails on the
first request after the mistake.

```kotlin
install(I18n) {
    messages = catalogs
    header = "X-Language"     // default: Accept-Language
    queryParameter = "lang"   // default: null, i.e. off
}
```

`header` exists for a service behind something that rewrites the client's own.

`queryParameter` is **off by default, deliberately**. `?lang=fr` is genuinely useful for a link
somebody sends a colleague, and it is also a second thing the same URL can mean, which every cache
in front of the service has to be told about. That is a decision to take, not a default to inherit.
When it is on, it beats the header — it is the more deliberate of the two.

## Why the plugins are not in the libraries they wrap

So that `shared-i18n` stays free of Ktor. A worker, a CLI or a Kafka consumer translates the same
messages and has no server in it, and a catalog library that drags a web framework behind it is one
those callers cannot use. The same is true of `shared-redis` and the rest: the library knows the
backend, this module knows the framework, and neither has to know both.
