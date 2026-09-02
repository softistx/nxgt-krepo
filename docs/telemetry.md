# Telemetry reference

What a `stx-telemetry` call may say. The [module README](../libs/stx-telemetry/stx-telemetry/README.md)
is why the library is shaped this way; this page is the vocabulary;
[`examples/spring-orders`](../examples/spring-orders/README.md) is an application using it — a server
span per request, one `span { }` in the service, typed events in a file of their own, and the whole
lot into a Mongo collection.

- [The root](#the-root)
- [Logging](#logging)
- [Spans](#spans)
- [Context](#context)
- [Attributes](#attributes)
- [Trace identity and `traceparent`](#trace-identity-and-traceparent)
- [Sampling](#sampling)
- [Exporters](#exporters)
- [The signal model](#the-signal-model)
- [The whole thing](#the-whole-thing)

## The root

```kotlin
val telemetry = Telemetry("checkout") {
    version = "1.4.0"
    environment = "production"
    attributes = attributesOf("region" to "eu-west-1")
    sampler = Sampler.ratio(0.1)
    minimum = Severity.Info
    stackTraces = true
    batch = 512
    linger = 1.seconds
    drainTimeout = 10.seconds
    onExportError = { it.printStackTrace() }
    export(ConsoleExporter())
}.install()
```

`Telemetry(service)` takes the service name positionally and has no default for it: `service.name` is
the one attribute every backend groups by, and a fleet of processes all called `unknown_service` is a
fleet with no telemetry.

| Setting | Default | What it decides |
| --- | --- | --- |
| `version` | none | `service.version` on every signal |
| `environment` | none | `deployment.environment.name` — `production`, `staging` |
| `attributes` | empty | Carried by every signal this process emits: a region, a pod, a tenant |
| `sampler` | `Sampler.always` | Which traces are kept. Asked once per trace, at its root |
| `minimum` | `Severity.Info` | The lowest severity emitted at all. Does not affect spans |
| `stackTraces` | `true` | Whether a failure's stack trace is rendered into the signal |
| `batch` | `512` | How many signals ship together at most |
| `linger` | `1s` | How long a partial batch waits for company before shipping anyway |
| `drainTimeout` | `10s` | How long `close()` waits for the queue before giving up on it |
| `onExportError` | prints to `System.err` | What happens when an exporter throws |
| `export(…)` | none | Adds a destination. Every signal goes to every one, in order |

| Member | What it does |
| --- | --- |
| `install()` | Makes this the telemetry that code outside a `withTelemetry` finds. Returns itself |
| `close()` | Drains the queue, ships it, closes the exporters, stands down as the default. Safe twice |
| `resource` | What this process reports itself as |
| `minimum` | The severity floor, readable |
| `Telemetry.installed` | The process-wide default, or `null` before anything is installed |

`close()` **blocks** — a shutdown hook, a `use` block and a container teardown cannot suspend, and a
close that returned before the backlog shipped would lose the signals that explain the shutdown.

## Logging

```kotlin
private val log = logger<CheckoutService>()   // named after the class
private val outbox = logger("outbox")         // named whatever you like
```

| Form | Example | When |
| --- | --- | --- |
| Typed | `log.info(Charged(id, amount))` | The one to reach for. The type's serial name is the event name; its fields are the attributes |
| Message and pairs | `log.warn("charge refused", "code" to c)` | Ad hoc, for what has no type yet |
| Lazy | `log.debug { "state: ${dump()}" }` | When building the message costs something. `debug` and `info` only |
| With a failure | `log.error("charge failed", failure, "orderId" to id)` | `warn` and `error` only |
| Typed with a failure | `log.error(Refused(code), failure)` | Both at once |

`log.enabled(Severity.Debug)` answers whether anything is listening, for the rare caller that wants
to skip work no lambda can defer.

Every one of these is a **non-suspending** function, so a log can be written from an `init` block, an
ordinary `catch`, or a Java callback. Every one is a silent no-op when nothing is installed, when the
severity is below the floor, or when a typed event will not serialise: a logging call is never the
thing that fails a request.

### Severities

| Severity | OTLP number | |
| --- | --- | --- |
| `Debug` | 5 | |
| `Info` | 9 | The default floor |
| `Warn` | 13 | |
| `Error` | 17 | |

There is deliberately no `trace` level: the word already means something else in this library, and a
fifth level below `debug` is one nobody agrees the meaning of. Something too fine for `debug` is an
attribute on a span, where it can be read next to the work it describes.

## Spans

```kotlin
span("charge", "orderId" to order.id, kind = SpanKind.Client) {
    attribute("processor", gateway.name)
    event("retrying", "attempt" to 2)
    status = SpanStatus.Error          // for work that failed without throwing
}
```

| Parameter | Default | |
| --- | --- | --- |
| `name` | — | What the span is called |
| `vararg attributes` | none | **Inherited**: logs and nested spans inside carry them too |
| `kind` | `SpanKind.Internal` | Named argument, because the varargs come before it |
| `block` | — | Runs with a `SpanScope` receiver |

`continuing(traceparent, name, …)` is the same thing for a trace that arrived over the wire: it reads
the header, or starts a fresh trace when the header is absent or malformed. `kind` defaults to
`SpanKind.Server` there.

### Inside the block — `SpanScope`

| Member | What it does |
| --- | --- |
| `attribute(name, value)` | One attribute on **this span only** |
| `attributes(vararg pairs)` | Several |
| `event(name, vararg pairs)` | A moment inside the span |
| `event(typed)` | The same, named by the type's serial name, fields as attributes |
| `name` | What the span is called. Settable, because a server span is `GET /orders/8d1f…` until routing has matched it and `GET /orders/{id}` after |
| `status` | `Ok` by default. A thrown exception overrides whatever is set here |
| `context` | This span's `SpanContext` |
| `traceId` / `spanId` | Its parts |
| `traceparent()` | The header to send with a call this span makes |

Attributes passed to `span(…)` are inherited by everything below; attributes set with
`SpanScope.attribute` belong to that span alone. Both appear on the span's own record.

### Kinds and statuses

| `SpanKind` | OTLP number | |
| --- | --- | --- |
| `Internal` | 1 | The default |
| `Server` | 2 | Handling an inbound request |
| `Client` | 3 | Making an outbound call |
| `Producer` | 4 | Publishing to a broker |
| `Consumer` | 5 | Handling a message from one |

| `SpanStatus` | Set when |
| --- | --- |
| `Ok` | The block returned, and nothing set otherwise |
| `Error` | The block threw, or the block set it |
| `Cancelled` | The block was cancelled — a timeout, a shutdown, a losing race |

`Cancelled` is a third status rather than an error so that a shutdown does not fill a dashboard with
failures that describe nothing but the shutdown, and so that a timeout is distinguishable from the
failure it was meant to prevent.

The exception always propagates. A span observes; it never swallows.

## Context

| Function | |
| --- | --- |
| `withTelemetry(telemetry) { }` | Runs the block against a specific root instead of the installed one. How a spec stays isolated |
| `withAttributes(vararg pairs) { }` | Adds fields every log and span inside carries — this library's answer to an MDC |
| `currentSpan()` | The span this code is in, or `null`. Non-suspending |
| `currentTraceparent()` | The header for an outgoing call, or `null` |

`withAttributes` outside any `withTelemetry` and with nothing installed simply runs the block.

## Attributes

An **attribute is a scalar**: a string, a number, a boolean, or a list of those. Anything with
structure is a `@Serializable` event type instead.

| Kotlin value | Becomes |
| --- | --- |
| `String`, `Boolean`, `Number` | The JSON scalar |
| `Enum` | Its `name` |
| `Instant`, `Duration` | Its `toString()` |
| `Iterable`, `Array` | A JSON array of the same conversions |
| `JsonElement` | Itself |
| `null` | JSON null |
| anything else | Its `toString()` — not refused, because a log call must never be the thing that fails |

`attributesOf("orderId" to id, "attempt" to n)` builds one directly; `Attributes` is a value class
over `Map<String, JsonElement>` and `+` merges, right-hand side winning.

## Trace identity and `traceparent`

| Type | |
| --- | --- |
| `TraceId` | 16 bytes as 32 lower-case hex characters. `random()`, `isValid`, `INVALID` |
| `SpanId` | 8 bytes as 16 hex characters. Same members |
| `SpanContext` | `traceId`, `spanId`, `sampled`, `remote` |

`SpanContext.traceparent()` renders the W3C header; `SpanContext.traceparent(header)` reads one and
returns `null` for anything unusable — an absent header, a malformed one, version `ff`, an all-zero
id. Null rather than an exception, because a malformed header is somebody else's bug arriving over
the network and the useful response is to start a fresh trace, not to fail a request that is
otherwise fine.

A version other than `00` is **read, not refused**: the specification says a future version keeps the
first four fields, so refusing would make this library the reason an upgraded caller loses its
traces.

## Sampling

| Sampler | |
| --- | --- |
| `Sampler.always` | The default |
| `Sampler.never` | Spans still run and still cost their attributes; nothing is exported |
| `Sampler.ratio(d)` | Keeps that share, decided by the trace id. `1.0` and `0.0` collapse to the two above |
| `Sampler { traceId -> … }` | A `fun interface`, so any lambda is one |

Asked **once, for the root span**. Every span under it inherits the answer, and a `traceparent`
carries it to the next service. Logs are emitted whether or not their trace is sampled.

## Exporters

```kotlin
interface Exporter : AutoCloseable {
    suspend fun export(resource: Resource, batch: List<Signal>)
    override fun close() {}
}
```

The `Resource` is passed on **every** call rather than handed over once at startup: it is constant
and small, and a hook that must be called before the first export is a hook somebody's
implementation will forget. Every destination that leaves this process needs it — OTLP puts it at
the root of its document — and an exporter that does not is free to ignore it.

Called from the single coroutine that owns the queue, so an implementation needs no synchronisation
and may take as long as it needs without blocking anybody who writes a log. It should not throw;
`onExportError` catches what does, and the remaining exporters still get the batch.

| Built in | |
| --- | --- |
| `ConsoleExporter(out = System.out, stackTraces = true)` | One human-readable line per signal, trace ids abbreviated. For a terminal |
| `JsonLinesExporter(out = System.out)` | One JSON object per line, discriminated by `"type": "log"` / `"span"`. For a collector |
| `FileExporter(path, maxSize = 64MB, every = 24.hours, keep = 7, compress = false)` | The same JSON lines, to a file that is rolled on size *and* on a period, with the oldest pruned. For a deployment with no collector |

`stx-telemetry-spring` adds the `stx.telemetry.*` keys — see
[`docs/spring-configuration.md`](spring-configuration.md) — which build and install a root for the
application and add every `Exporter` bean to it, plus a `CoWebFilter` that makes each request a
server span.

`stx-telemetry-ktor` adds `install(Observability) { … }`, which builds or adopts a `Telemetry` for an
application and opens a `SpanKind.Server` span per request — continuing an incoming `traceparent`,
recording `http.request.method`, `url.path`, `http.route` and `http.response.status_code`, and
renaming the span to the matched route once Ktor knows it. `call.telemetry`, `call.span` and
`call.traceparent` are what a handler reads.

`stx-telemetry-slf4j` adds `Slf4jExporter(spans, spanSeverity, factory)`, which writes logs to a
logger named after their source and spans to `com.softistx.telemetry.span`, putting the trace id and
the attributes in the MDC for the length of each call. The same module's `TelemetryServiceProvider`
is the bridge pointed the other way — third-party SLF4J logs into this pipeline — and the two cannot
both be used, which `Slf4jExporter` checks rather than looping.

`stx-telemetry-mongo` adds `MongoExporter(database, collection, retention)` and
`MongoExporter.connecting(uri, database, collection, retention)`, which writes one document per
signal — the JSON-lines fields, plus the resource, with the instants as BSON dates so Mongo can index
and expire them. Retention is a **TTL index**, so the deleting is Mongo's background task and not a
job in this process; changing it rebuilds the index rather than leaving the old window in place. The
`connecting` form opens a client of its own and closes it, which is what `stx.telemetry.mongo` uses:
telemetry on its own pool means a burst of it cannot exhaust the one business requests queue for.

`stx-telemetry-otlp` adds `OtlpExporter(endpoint, headers, timeout, attempts, backoff, gzip, onPartialSuccess, client)`,
which posts OTLP/HTTP+JSON to `<endpoint>/v1/logs` and `<endpoint>/v1/traces`. Its README has the
retry table and what a `partialSuccess` means; a client passed in is used and not closed.

`ConsoleExporter` writes everything to one stream, errors included: a stream per severity interleaves
unpredictably when both are a terminal, which reorders the very lines somebody is reading.

`FileExporter` writes the lines `JsonLinesExporter` writes, so the same parser reads both. Its two
limits are not alternatives: `maxSize` bounds the disk and `every` bounds how old the newest *closed*
file is, and a deployment that sets only one gets either yesterday's telemetry still in the open file
or a full disk before midnight. `every` is **aligned to the epoch** — `24.hours` rolls at UTC
midnight, not a day after the process started, so two processes started at different times cut their
files at the same moments. An empty file is never rolled, whatever the clock says: retention that
counted empty files would prune the full ones out of existence behind them. Closing rolls nothing and
the next start appends, so a service that restarts often keeps a week of files rather than a week of
deploys.

## The signal model

Everything that reaches an exporter is a `Signal`, and every one of these is `@Serializable`.

| Type | Fields |
| --- | --- |
| `LogRecord` | `at`, `severity`, `name`, `source`, `attributes`, `span`, `error` |
| `SpanRecord` | `name`, `context`, `parent`, `kind`, `startedAt`, `endedAt`, `status`, `attributes`, `events`, `error` |
| `SpanEvent` | `name`, `at`, `attributes` |
| `ErrorInfo` | `type`, `message`, `stackTrace` |
| `Resource` | `service`, `version`, `environment`, `attributes` |

`Signal` is one sealed type for logs and spans because everything downstream treats them the same
way: they queue together, batch together, and drain together. Only an exporter cares which it has,
and that is one `when` in one place. Metrics will join as a third variant.

`ErrorInfo` flattens a `Throwable` to strings at the point of failure, rather than carrying it
through a queue that may outlive the scope it came from — a `Throwable` holds references to whatever
was on the stack when it was built.

## The whole thing

Every section above is one verb. This is a request being handled: a trace that arrived over the wire,
a client span for the call it makes, typed events, and the header it hands on.

```kotlin
@Serializable @SerialName("checkout.charged")
data class Charged(val orderId: String, val amount: Long)

@Serializable @SerialName("checkout.refused")
data class Refused(val orderId: String, val code: String)

private val log = logger<CheckoutService>()

suspend fun checkout(gateway: Gateway, orderId: String, amount: Long, incoming: String?): Boolean =
    continuing(incoming, "POST /checkout", "http.route" to "/checkout") {
        name = "POST /checkout/{id}"

        withAttributes("order.id" to orderId) {
            span("charge", kind = SpanKind.Client) {
                attribute("processor", gateway.name)

                val ok = gateway.charge(orderId, amount, traceparent())
                if (ok) {
                    log.info(Charged(orderId, amount))
                } else {
                    status = SpanStatus.Error
                    log.warn(Refused(orderId, "limit"))
                }
                ok
            }
        }
    }
```

Given the inbound header
`00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`, that emits two spans and one log, related
like this:

| | `traceId` | `parent` | |
| --- | --- | --- | --- |
| `POST /checkout/{id}` | `4bf92f35…4736` | `00f067aa0ba902b7` | The caller's trace, continued — not a new one |
| `charge` | `4bf92f35…4736` | the server span's id | `SpanKind.Client`, and `traceparent()` inside it is what the gateway is handed |
| `checkout.charged` | `4bf92f35…4736` | the `charge` span's id | A log carries the span it was written in |

Six things in those twenty lines are decisions rather than syntax:

- **`continuing` and not `span`.** It reads the inbound header, so this process's spans join the
  caller's trace instead of starting one that nothing can be correlated with. A malformed or absent
  header starts a fresh trace rather than failing, which is what lets the same function serve a
  caller that does not propagate.
- **`name` is reassigned inside the block.** A server span is `POST /checkout` until routing has
  matched it and `POST /checkout/{id}` after — a name carrying the id is a cardinality problem in
  every backend that groups by it.
- **`withAttributes` and `attribute` are not the same thing, and the difference is measurable.**
  `order.id` is on the `withAttributes`, so it appears on the `charge` span *and* on the log written
  inside it. `processor` is set with `SpanScope.attribute`, so it appears on that span **and on
  nothing else** — the log does not carry it.
- **`traceparent()` rather than a header assembled by hand.** It is the current span's context in
  the W3C format, and it is what makes the gateway's own spans children of this one.
- **`status = SpanStatus.Error` with no exception thrown.** A refusal is a business outcome, not a
  failure of this code; the span says the work did not succeed while the function returns normally.
  A thrown exception would set the same status and propagate, which is the other half of the same
  rule: a span observes, it never swallows.
- **`@SerialName` on the event types.** The serial name *is* the event name, so without it the name
  is the class's — which changes when the class moves package, and takes every dashboard built on it
  with it.

### The root, and the shutdown

```kotlin
fun main() {
    val telemetry = Telemetry("checkout") {
        version = "1.4.0"
        environment = "production"
        attributes = attributesOf("region" to "eu-west-1")
        sampler = Sampler.ratio(0.1)
        export(OtlpExporter("http://localhost:4318"))
    }.install()

    Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted { telemetry.close() })

    server.start(wait = true)
}
```

`install()` is what makes `log` and `span` outside any `withTelemetry` find this root — without it
every call above is a silent no-op, which is the correct behaviour for a library opening spans in an
application that has never heard of this one, and the wrong one for the application itself.

**`close()` blocks, and it has to.** A shutdown hook cannot suspend, and a close that returned before
the backlog shipped would drop exactly the signals that explain the shutdown. It is `unstarted`
rather than `startVirtualThread` because a hook must be registered before it runs.

The sampler is asked **once per trace, at its root** — so a sampled-out trace costs nothing further
down, and a trace that arrived sampled stays sampled through this process. `Sampler.ratio(0.1)` on
a service that only ever continues somebody else's traces samples nothing of its own, which is the
intended reading rather than a surprise.
