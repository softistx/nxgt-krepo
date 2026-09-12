# stx-i18n-ktor

`install(I18n)` — the locale for each request, negotiated once and handed to the route.

**`io.github.softistx:stx-i18n-ktor`** — [how to depend on it](../../../../docs/consuming.md).

```kotlin
install(I18n) { messages = Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH)) }

get("/greeting") { call.respondText(call.translate("hello.world", mapOf("name" to "Ada"))) }
```

## The odd one, in a good way

Every other plugin in this repo owns a connection. This one owns nothing and resolves something
**per request**: the locale is negotiated at `CallSetup` and kept on the call.

That is not an optimisation. Negotiating inside each handler that needs a message parses the same
header several times, and — worse — lets one response answer two questions in two different
languages. Resolving once at the edge makes that impossible rather than unlikely.

`Application.messages` is the catalogs, per application. `ApplicationCall.translator` is this
request's translator, per request. The two are different scopes on purpose, and the second throws
when the plugin is not installed rather than quietly answering in English — a service whose
translations silently stopped negotiating is worse off than one that fails on the first request
after the mistake.

## `header` and `queryParameter`

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
When it is on it beats the header, being the more deliberate of the two.

## Why the plugin is not in `stx-i18n`

So the catalogs do not drag Ktor in behind them. A worker, a CLI or a Kafka consumer translates the
same messages and has no server in it, and a library that pulls a web framework behind it is one
those callers cannot use. The library knows the messages, this module knows the framework, and
neither has to know both.

It used to live in `stx-ktor` as `com.softistx.ktor.i18n`, alongside six other integrations.
Publishing it beside its own library is what the other families here already do, and it lets the
`stx-i18n` dependency be `exported` instead of `compile-only`.

---

Apache-2.0 · [Contributing](../../../../CONTRIBUTING.md) · [All the libraries](../../../../README.md)
