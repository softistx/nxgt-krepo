# shared-ktor-i18n

The `Accept-Language` half of [`shared-i18n`](../shared-i18n/README.md), as a Ktor server plugin.

```kotlin
install(I18n) { messages = Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH)) }

get("/greeting") {
    call.respondText(call.translate("hello.world", mapOf("name" to "Ada")))
}
```

That is the whole API, plus `call.translator` when a handler wants the `Translator` itself.

## Resolved once per call

The locale is negotiated at call setup and kept on the call. Negotiating inside each handler that
needs a message would parse the same header several times and — worse — could answer two questions
in one response in two different languages.

`call.translator` throws when the plugin is not installed, rather than quietly answering in English.
A service whose translations silently stopped negotiating is worse off than one that fails on the
first request after the mistake.

## Configuration

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

## Why a separate module

So that `shared-i18n` stays free of Ktor. A worker, a CLI or a Kafka consumer translates the same
messages and has no server in it, and a catalog library that drags a web framework behind it is one
those callers cannot use.

The name is the family, not this integration: `shared-ktor-*` is where Ktor integrations for the
other shared libraries go as they appear. One flat `shared-ktor` holding all of them would make an
app that wants only this plugin carry the Redis, Mongo, Kafka and AMQP drivers, and would gate its
test run on every one of those servers being up.
