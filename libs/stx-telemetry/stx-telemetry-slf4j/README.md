# stx-telemetry-slf4j

The bridge between `stx-telemetry` and SLF4J, in both directions. Pick one.

**`io.github.softistx:stx-telemetry-slf4j`** — [how to depend on it](../../../docs/consuming.md).

| Direction | What it is for |
| --- | --- |
| **Inbound** — `TelemetryServiceProvider` | Third-party libraries' logs enter this pipeline, carrying the current span |
| **Outbound** — `Slf4jExporter` | This library's signals leave through an SLF4J binding you already have |

## Inbound: SLF4J logs into stx-telemetry

Putting this module on the classpath is the whole of the configuration. SLF4J 2.x finds
`META-INF/services/org.slf4j.spi.SLF4JServiceProvider`, and every library that logs — Lettuce,
Hibernate, the Ktor engine, the Kafka and Mongo drivers, Spring itself — writes into the same
pipeline as `logger<T>()`.

That is the point of it. A driver's *connection reset* and the request it happened under are worth
very little to each other in two different files, and a bridged log picks up the current span for
free, because `com.softistx.telemetry.Logger` is what it delegates to.

**This takes over SLF4J.** SLF4J binds one provider; if logback is also on the classpath it prints a
warning and picks one. Which one is not a thing to leave to the classpath order:

```
-Dslf4j.provider=com.softistx.telemetry.slf4j.TelemetryServiceProvider
```

An application that wants to keep logback should not depend on this module for the inbound half at
all — it should use `Slf4jExporter` below.

Before a `Telemetry` is installed, bridged logs are dropped in silence. Logging is initialised by the
first library that logs, which is routinely earlier than an application's own startup; refusing,
buffering, or printing a warning would each be worse than dropping a line nobody had yet said where
to send.

### The MDC, which this library otherwise argues against

The provider installs a working `BasicMDCAdapter`, and `TelemetryLogger` reads the MDC into the
record's attributes. That looks like a contradiction and is not.

The objection to an MDC is that it is a thread-local **nobody updates when the work moves**, so it is
wrong after a suspension. Third-party blocking code that calls `MDC.put` has no other way to say
anything, and on its own thread, synchronously, around its own log call, the MDC is exactly right.
What is never done here is carrying it across a hop: `stx-telemetry`'s own loggers never look at it,
and the current span comes from the coroutine context as always.

## Outbound: stx-telemetry into an SLF4J you already have

```kotlin
Telemetry("checkout") { export(Slf4jExporter()) }.install()
```

For an application with logback, an appender fleet and a log pipeline it trusts, that wants `span { }`
and typed events without changing where anything ends up. Logs go to a logger named after their
source; completed spans go to `com.softistx.telemetry.span`, one line each, and can be turned off.

The trace id and the attributes go in the **MDC** for the length of one call, because `%X{traceId}`
is where a logback pattern reads them. Same mechanism, used the one way it is safe: the exporter runs
on the pipeline's single consumer and the keys are removed in a `finally`, with no suspension in
between. A bridge that left keys behind would put this batch's order id on the next batch's lines —
the exact failure this library exists to describe.

`ErrorInfo` was flattened to strings when the signal was created, so what reaches an appender is a
carrier holding the recorded type, message and stack trace, rather than a fresh `Throwable`
pretending to have been thrown here.

## Both at once is a loop

The provider makes SLF4J write into `stx-telemetry`; the exporter writes `stx-telemetry` into SLF4J.
Together they are a cycle that takes a process down rather than misbehaving visibly, so
`Slf4jExporter` refuses to be constructed when the bound SLF4J factory is this module's own. The
message says how to choose a direction.

That guard is tested against the real configuration rather than a contrived one: in this module's own
test runtime the bound provider *is* `TelemetryServiceProvider`, which is also what proves the SPI
resource survives packaging.

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
