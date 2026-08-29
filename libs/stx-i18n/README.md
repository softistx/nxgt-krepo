# stx-i18n

Message catalogs, loaded once at startup and immutable afterwards.

```kotlin
val messages = Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH))

messages.forLocale(Locale.FRENCH).translate("hello.world", mapOf("name" to "Ada"))
```

Catalogs are `.properties` files laid out the way `ResourceBundle` projects already lay them out —
`locales/messages.properties`, `locales/messages_fr.properties`, `locales/messages_fr_CA.properties`
— and existing ones drop in unchanged. What is underneath them is not `ResourceBundle`, for reasons
below.

## Shape

```
Messages       every catalog, compiled; the locale walk; negotiate(); audit()
Translator     a view bound to one locale — translate(key, args)
MessageSource  where catalogs come from: PropertiesSource, MapSource
MissingKey     ReturnKey (production) or Fail (tests)
CatalogAudit   what each locale is missing against the fallback
```

**No locks, no `@Volatile`, nothing to synchronise.** Everything is built during `load` and never
written to again, which is the cheapest concurrency story there is. The one exception is ICU's
`MessageFormat`, which is documented as unsynchronized and is therefore formatted under a lock — and
only for the messages that actually take arguments.

## Loading is eager, and that is the point

Every ICU pattern is parsed during `load`. A `{` somebody left open is then a failed startup rather
than a failed screen three weeks later, in the one language nobody on the team reads. It also
removes a per-call parse: parsing a pattern costs far more than formatting with one.

The locale list is the caller's. A library with a hard-coded list of languages is a library you have
to edit — and re-release — to ship a new one.

## Lookup walks, per key

For a message in `fr-CA`: `fr-CA`, then `fr`, then the fallback locale and its own chain, then the
unsuffixed base catalog, then the [`MissingKey`](#a-missing-key-is-a-policy) policy.

Walking **per key** rather than choosing one catalog up front is what lets `fr-CA` be a handful of
overrides instead of a full copy of `fr`. It is also the difference between a French user seeing the
English text for a key nobody has translated yet and seeing `checkout.button`.

The unsuffixed `messages.properties` sits underneath everything and is compiled with the *fallback*
locale, not `Locale.ROOT` — `ROOT`'s plural rules have only an `other` form, so a root-compiled
`{count, plural, one {# order} other {# orders}}` renders "1 orders".

## A missing key is a policy

`MissingKey.ReturnKey` returns the key itself. `checkout.button` on a page is ugly and diagnosable;
a 500 in its place is neither.

`MissingKey.Fail` throws. A key typed wrongly is a failing spec here and a support ticket otherwise,
and the difference between those two outcomes is which of these was configured.

Under `Fail`, a message whose arguments were not supplied fails too. ICU is lenient about that — it
leaves `{count}` in the output rather than complaining, which is right for a running server and
useless to a test — so the check is explicit, against the argument names read off the pattern.

## Drift is detectable, so detect it

```kotlin
messages.audit().isClean() shouldBe true
```

Translation drift is the standing maintenance problem of every catalog: the project this module was
written for had 55 keys in `messages.properties` and 62 in `messages_fr.properties`, and nothing
noticed. `audit()` reports, per locale, what the fallback answers that it cannot and what only it
has — measured through the locale's own chain, so `fr-CA` overriding two keys is complete as long as
`fr` is.

## Negotiation

```kotlin
messages.negotiate("fr-CA,fr;q=0.9,en;q=0.8")   // → the fr-CA catalog
```

RFC 4647 lookup over the locales actually shipped, quality values honoured. A malformed header is a
fallback, never a failed request — the header comes from a client nobody controls.

This is the half an Android app has no use for, where the locale is a setting. See
[`libs/stx-ktor`](../stx-ktor/README.md) for the server plugin over it.

## Catalogs are read as UTF-8, strictly

`PropertiesSource` decodes with `CodingErrorAction.REPORT`, so a Latin-1 file fails to load rather
than arriving as mojibake. `ResourceBundle` on Java 9+ retries Latin-1 when the UTF-8 decode fails,
which sounds forgiving and is not: a file that is *mostly* valid UTF-8 with one stray byte decodes
"successfully" and is wrong in one place, and every tool that is not the JDK's bundle reader gets it
wrong too. Loudly is better.

`ResourceBundle.getBundle` is also never called, for a second reason: it falls back to the **JVM
default locale** when the requested bundle is absent. Asking for `es` on a machine with
`LANG=fr_FR` silently returns French. A server's answer must not depend on its host's environment.

## Why this is not in stx-common

ICU4J is a 15 MB jar. `stx-common`'s rule is kotlinx-and-nothing-else, and putting message
formatting in it would make `stx-kafka` carry a formatting library it will never call.
