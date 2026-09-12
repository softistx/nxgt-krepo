# stx-telemetry-otlp

Ships `stx-telemetry`'s logs and traces to an OTLP collector over HTTP, in JSON.

**`io.github.softistx:stx-telemetry-otlp`** — [how to depend on it](../../../../docs/consuming.md).

```kotlin
Telemetry("checkout") {
    environment = "production"
    export(OtlpExporter("http://otel-collector:4318"))
}.install()
```

`docs/telemetry.md` is the vocabulary. This page is the two decisions.

## Why there is no OpenTelemetry Java SDK here

OTLP over HTTP has a JSON encoding, and the protobuf-to-JSON mapping is specified. A document is
thirty lines of `@Serializable` data classes — `Document.kt` is the whole of it — and the transport
is `java.net.http.HttpClient`, which has been in the JDK since 11 and is the JDK this repository
already targets. **This module's only dependency is `stx-telemetry`.**

The SDK would bring its own `Context`, held in a `ThreadLocal`, which is the single thing
`stx-telemetry` exists to not have. Adopting it to send a JSON document would mean either running two
notions of "the current span" side by side, or building the library on the mechanism that is wrong
under coroutines. It would also bring a dependency tree — the API, the SDK, the exporter, the
semantic conventions — to carry a format we can write out directly.

That trade would be a bad one if the encoding were subtle. It is not, but two details are easy to get
wrong and both are in `Document.kt`:

- **A 64-bit number is a JSON string.** `timeUnixNano` and `intValue` are `fixed64`/`int64`, and
  JSON's number type cannot hold one without losing the low bits. Every receiver expects the quotes.
- **Trace and span ids are hex, not base64.** The specification makes them an exception to the usual
  `bytes` encoding — convenient, since `stx-telemetry` already keeps them as hex.

`OtlpExporterTest` pins the document against a `com.sun.net.httpserver.HttpServer`, which ships with
the JVM and starts in a millisecond. But a stub agrees with whatever we wrote, so there is a second
spec, gated on an environment variable, that asks a real collector:

```bash
OTLP_TEST_ENDPOINT=http://localhost:4318 ./kotlin test -m stx-telemetry-otlp
```

It asserts that `partialSuccess` comes back **empty** — a collector answers `200` to almost anything,
and `partialSuccess` is where it admits to having thrown part of the document away.

## What is retried, and what deliberately is not

| The collector says | What happens |
| --- | --- |
| 2xx, empty `partialSuccess` | Done |
| 408, 429, 5xx | Sent again, up to `attempts`, with a doubling `backoff` |
| Any other 4xx | `OtlpRefusedException` at once — the same document would be wrong again |
| Nothing (connection failure) | Retried, then `OtlpUnreachableException` |
| 2xx, non-empty `partialSuccess` | `onPartialSuccess`, and **no retry** |

That last row is the interesting one. A partial success means the collector took some records and
refused others; the specification is explicit that resending is wrong, because the accepted records
would arrive twice. So it is *reported* rather than acted on — by default as an `OtlpRejectedException`,
which reaches the pipeline's `onExportError` like every other export failure. Silence there would mean
a fleet quietly losing a share of its telemetry with a green dashboard.

Everything this module throws is an `OtlpException` and reaches `onExportError` and no further: a
collector being down is not a reason for a request to fail.

## Two smaller choices

**gzip is on by default.** Every OTLP/HTTP receiver is required to understand it, telemetry is the
most compressible traffic a service produces, and the cost is a `GZIPOutputStream` on a coroutine
that is not on anybody's critical path. `gzip = false` for a collector behind something that mangles
it.

**A client you pass in is not closed.** `OtlpExporter(endpoint, client = existing)` uses it and
leaves it open, on the rule the rest of the repository follows: close only what you opened. An
application with a tuned `HttpClient` — a proxy, a truststore, a connection budget — should not get a
second one because it turned telemetry on, and should not lose the first one when telemetry shuts
down.

---

Apache-2.0 · [Contributing](../../../../CONTRIBUTING.md) · [All the libraries](../../../../README.md)
