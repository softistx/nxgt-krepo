# stx-i18n-spring

Message catalogs for a Spring Boot application, behind one property — and a locale resolver narrowed
to the languages the catalogs actually cover.

**`io.github.softistx:stx-i18n-spring`** — [how to depend on it](../../../../docs/consuming.md).

```yaml
stx:
  i18n: { enabled: true, languages: [en, fr], fallback: fr }
```

```kotlin
class Welcome(private val messages: Messages)   // injected like any other bean
```

## The resolver is the half that matters

A `Messages` bean on its own would be a catalog nobody consults. The other half is that this
narrows `localeContextResolver` to the languages it loaded — because WebFlux's default answers with
whatever `Accept-Language` asked for, catalog or no catalog, so a request for `de` gets `de` and
then finds no messages in it.

That makes the outcome a property of **ordering**, since there is exactly one bean of that name and
two auto-configurations that want to be it. `LocaleResolverPrecedenceTest` is that spec, and it
exists because the answer was measured and was wrong the first time: before
`@AutoConfiguration(before = WebFluxAutoConfiguration::class)`, Boot's resolver won in every
arrangement.

## Where a `stx.*` key overlaps one of Boot's, Boot's wins

`BootLocaleUnset` is a `SpringBootCondition` that matches only when the application has set neither
`spring.web.locale` nor `spring.web.locale-resolver`. An application that wrote one of those down
meant it, and a library that silently ignored the framework's own property in favour of its own
would be one nobody could reason about from an `application.yaml`.

A condition rather than `@ConditionalOnProperty`, because there is no "this property is absent" form
of that annotation and there are two keys to check. It also reads correctly in the condition
evaluation report, which is where somebody looks when the resolver is not the one they expected.

## The languages are a property

The version this replaces hardcoded `listOf("en", "fr")`, so adding a language meant editing the
framework rather than the deployment. Its spec still says so.

## The one integration that needs WebFlux

Its six siblings depend on `spring-boot-starter`; this one needs `spring-boot-starter-webflux`,
because it is the only one that *replaces* a framework bean rather than only adding its own.
`LocaleContextResolver` is a Spring Web type and `WebFluxAutoConfiguration` is what this orders
itself before.

## Why this is a module and not a package in `stx-spring-boot`

It used to be `com.softistx.spring.integration.i18n`, and it was the **last** of the seven to leave —
after it, `stx-spring-boot` has no `integration/` package at all.

The hub still depends on `stx-i18n`, and that is not a leftover: `RequestTranslator` and the
translated error body are `stx-spring-boot`'s own features, and `Messages` is a constructor
parameter of its exception handler. What moved is the auto-configuration, not the use.

---

Apache-2.0 · [Contributing](../../../../CONTRIBUTING.md) · [All the libraries](../../../../README.md)
