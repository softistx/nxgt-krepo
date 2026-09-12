# stx-telemetry

Logs and traces for a Kotlin service that is made of coroutines.

**`io.github.softistx:stx-telemetry`** — [how to depend on it](../../../../docs/consuming.md).

```kotlin
@Serializable
@SerialName("checkout.charged")
data class Charged(val orderId: String, val amount: Long)

private val log = logger<CheckoutService>()

Telemetry("checkout") {
    environment = "production"
    sampler = Sampler.ratio(0.1)
    export(ConsoleExporter())
}.install()

span("charge", "orderId" to order.id) {
    log.info(Charged(order.id, amount))   // carries this span's traceId and spanId
    payments.charge(order.card)
}
```

`docs/telemetry.md` is the vocabulary — every verb, every severity, the span model, the attribute
rules. This page is the reasoning: four decisions, and what each one refuses.

## The current span is a coroutine context element, not a thread-local

This is the whole design, and the rest follows from it.

The repository has already written the argument, three times, about something else:

> **The locale comes from the exchange, not from `LocaleContextHolder`.** That holder is a
> `ThreadLocal`, and WebFlux is the Spring stack where a request is not a thread […] it looks right
> in development and starts serving French to English speakers under load — **a bug with no stack
> trace and no failing test.**
>
> — `libs/core/stx-spring-boot/src/i18n/RequestTranslator.kt`

SLF4J's MDC *is* that `ThreadLocal`. A request suspends on a database call and resumes on whichever
worker is free; whatever the MDC held is now whatever the previous request left there. An
observability library built on it would reproduce, **in the tool meant to make such bugs visible**,
precisely the bug this repository refuses to have — and it would do it silently, because a log line
with the wrong trace id looks exactly like a log line.

So `span { }` puts a `TelemetryContext` in the coroutine context, and it travels the way coroutine
context does: into everything launched inside, out of nothing, across every dispatcher hop, correct
after every suspension. `test/context/ContextTest.kt` is that claim under test — a span that survives
fifty suspensions on a shared pool, two sibling coroutines that cannot see each other's span. Every
scenario in that file is one an MDC fails.

### Then why is there a thread-local in here after all

Because `log.info(…)` must not be a suspending function. A log written from an `init` block, from a
`catch` in ordinary blocking code, or from a Java callback is a log that still has to come out, and
a suspending logger cannot be called from any of them.

`TelemetryContext` is therefore *also* a `ThreadContextElement`: the coroutine runtime calls
`updateThreadContext` on every dispatch and `restoreThreadContext` on every suspension, which keeps a
plain thread-local in step with the coroutine context **automatically**.

The distinction is exact and it is the point:

|  | MDC | the mirror here |
| --- | --- | --- |
| Source of truth | the thread-local itself | the coroutine context |
| Who updates it when the work moves | nobody | the coroutine runtime, at every dispatch |
| After a suspension | the previous request's values | this coroutine's values |
| In a sibling coroutine | whatever ran on that thread last | absent |

Same mechanism, opposite correctness — because one is a cache with an owner and the other is a
variable with no owner at all.

## An event is a `@Serializable` type

```kotlin
log.info(Charged(order.id, amount))                          // the type names it, its fields are the attributes
log.warn("charge refused", "orderId" to id, "code" to code)  // ad hoc, for what has no type yet
log.debug { "state: ${expensive()}" }                        // lazy
```

The typed form is what "kotlinx.serialization first-class" means here, and it is not a convenience.
A `toString()` on a domain object logs whatever fields the object happens to have — including the one
added next quarter, including the card number — and nobody finds out, because a log that says too
much still looks like a working log. Declaring the type you log makes **choosing what is logged the
same act as writing the code**, rather than a redaction list somebody has to keep up to date. It is
the same argument `stx-common`'s decoding helper makes when it deliberately keeps the offending text
out of the exception it throws.

`@SerialName("checkout.charged")` is how an event is named. Without one the name is the type's
qualified name: precise, greppable, and not pretty.

An **attribute is a scalar** — string, number, boolean, or a list of those. That is what a backend
can index, filter and group by, and it is what OTLP accepts. Anything with structure is an event
type, not an attribute.

## Writing a signal never waits and never fails

`Telemetry` hands every signal to `stx-common`'s `Mailbox`, whose `post` is a `trySend` on an
unbounded channel: it always succeeds, never suspends, and works from any thread. One coroutine
drains it, which is what lets the batch buffer be a plain `ArrayList` with no synchronisation
anywhere and what makes a batch's order the order things happened in. `AmqpPublisher` uses the same
shape for the same reason.

A bounded queue would answer the back-pressure question by dropping signals or by blocking the
application, and neither is an answer. The consequences are all deliberate:

- **No telemetry installed** — the log is dropped in silence. A library that logs must work inside an
  application that has never heard of this one.
- **An exporter throws** — it is reported to `onExportError` and the next exporter still gets the
  batch. A collector being down is not a reason for a request to fail.
- **An event will not serialise** — the log is written with no attributes rather than not at all.

`close()` is the one thing here that blocks, and it has to: a shutdown hook cannot suspend, and a
close that returned before the backlog shipped would lose exactly the signals a shutdown most needs
to explain itself. `drainTimeout` bounds it, so a collector that stopped answering does not become
the reason a process will not exit.

## Sampling is decided once, by the root

A sampler is asked for the root span and the answer travels with the trace — down through every
child, and out over the wire in the `traceparent`. A sampler consulted per span produces traces
missing their middles, and a gap in a trace looks like work that never happened.

The decision is a function of the trace id rather than a coin toss, so two services at the same ratio
make the **same** decision about the same trace. Two services tossing independently at 10% keep a
whole trace 1% of the time.

Logs are not sampled. Sampling is a decision about the volume of traces, and a log dropped because
its trace was not kept is a log missing at precisely the moment somebody is reading logs to find out
what happened. The `traceId` is attached either way, so an unsampled trace's logs still group.

## What is not here

- **No metrics yet.** They belong on this pipeline and will arrive as a third `Signal`; nothing in
  the model or the exporter contract has to change to admit them.
- **No OpenTelemetry SDK.** `stx-telemetry-otlp` speaks OTLP/HTTP with kotlinx.serialization and the
  JDK's own `HttpClient`. The Java SDK would bring its own `Context` on a `ThreadLocal` — the thing
  this library exists to not have — and a transitive dependency tree to hold it.
- **No global to log through.** `logger<T>()` resolves its telemetry at each call: the one in scope
  first, the installed default second. That order is what lets two specs in one JVM collect their own
  signals, and it is why `withTelemetry` exists.

---

Apache-2.0 · [Contributing](../../../../CONTRIBUTING.md) · [All the libraries](../../../../README.md)
