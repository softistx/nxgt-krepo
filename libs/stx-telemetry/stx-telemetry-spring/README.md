# stx-telemetry-spring

One `stx-telemetry` root for the application, and a server span per request.

**`io.github.softistx:stx-telemetry-spring`** — [how to depend on it](../../../docs/consuming.md).

```yaml
spring:
  application: { name: checkout }
stx:
  telemetry:
    enabled: true
    environment: production
    sample-ratio: 0.1
    otlp: { enabled: true, endpoint: http://otel-collector:4318 }
```

```kotlin
@Service
class Checkouts {
    private val log = logger<Checkouts>()

    suspend fun charge(order: Order) = span("charge", "orderId" to order.id) {
        log.info(Charged(order.id, order.amount))   // carries the request's trace
    }
}
```

`docs/spring-configuration.md` has every key. This page is the two decisions.

## `CoWebFilter`, and not `WebFilter`

A `WebFilter` returns a `Mono`, so anything it puts in scope lives in the **Reactor context** — and
the handler that matters is a suspending `@RestController` method, which does not read the Reactor
context. A span opened in a `WebFilter` would be invisible to the very code it is supposed to cover.

`CoWebFilter` is Spring's own answer to exactly that: it runs the chain inside a coroutine and hands
that coroutine's context on to the suspending handler. So a `CoroutineContext.Element` installed here
is in scope there, and `logger<T>()` in a `@Service` three layers down carries the request's trace
with nothing passed to it.

`TelemetryWebFilterTest` is that claim under test against a real WebFlux dispatch, and it is the
first scenario in the file because it is the reason the filter is shaped the way it is.

**It costs one dependency and the specs are how that was found.** `CoWebFilter` is implemented with
`mono { }`, so it does nothing at all without `kotlinx-coroutines-reactor`, which the WebFlux starter
does not bring. Every filter scenario — including the one that only calls `chain.filter` — hung on a
five-second timeout until the manifest said so.

## A 200 that nobody set

WebFlux sets a response status only when something asked for one: a `@GetMapping` returning a
`String` leaves `statusCode` null and the engine writes 200 at commit, which is *after* this span is
written. So a null reads as 200 — which is what Spring Boot's own metrics do, for the same reason.

A handler that **threw** also leaves the status null, and calling that a 200 would be worse than
recording nothing. So the filter tracks whether the chain completed, and a failed request gets no
status code at all; the failure on the span says what happened. Both halves are pinned by specs.

## The rules this module inherits

- **Opt-in, with no `matchIfMissing`.** Putting this library on a classpath must not change where an
  application's logs go. `stx.telemetry.web-filter` is a *behaviour* flag on a bean that always
  exists rather than a second `@ConditionalOnProperty` — a `matchIfMissing = true` would read like an
  exception to the rule while meaning something else.
- **Every `Exporter` bean is added**, alongside whatever the properties asked for. An application
  with a destination of its own declares a bean and says nothing else. `stx.telemetry.otlp` exists so
  the common case needs no `@Bean` method, and is nested behind `@ConditionalOnClass` so an
  application that exports some other way is not made to carry `stx-telemetry-otlp`.
- **An application's own `Telemetry` bean wins**, through `@ConditionalOnMissingBean`.
- **The metadata is hand-written**, because `spring-boot-configuration-processor` is a Java
  annotation processor and this toolchain has no kapt. `ConfigurationMetadataTest` keeps it honest
  against the code and `ConfigurationDocsTest` keeps the reference page honest against it.

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
