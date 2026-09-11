# stx-telemetry-ktor

One telemetry per application, and a server span per request.

**`io.github.softistx:stx-telemetry-ktor`** — [how to depend on it](../../../docs/consuming.md).

```kotlin
install(Observability) {
    service = "checkout"
    environment = "production"
    export(OtlpExporter("http://otel-collector:4318"))
}

get("/orders/{id}") {
    log.info(Fetched(call.parameters["id"]!!))   // carries this request's trace
}
```

`docs/telemetry.md` is the vocabulary. This page is why the plugin is shaped the way it is.

## The span wraps the pipeline, rather than being two hooks

Every other plugin in this repository does its work in `on(CallSetup)`. This one cannot: a
`CoroutineContext.Element` is only in scope for the code that runs *inside* the `withContext` that
installed it, and a span opened in one hook and closed in another has no way to be in scope for the
handler in between.

So the plugin intercepts at `ApplicationCallPipeline.Monitoring` and calls `proceed()` inside the
span. Everything the route does — every coroutine it launches, every library that logs through
SLF4J, every nested `span { }` — is inside it, and the whole request is one trace.

## The name is fixed after routing, not before

A span is opened before Ktor knows which route will match, so it starts as `GET /orders/8d1f-…` —
the raw path, which is a cardinality disaster as a span name and exactly what you want to see when
hunting a 404. Ktor publishes the matched route on `RoutingRoot.RoutingCallStarted`, so the plugin
records the template there and renames the span to `GET /orders/{id}` before it is written.

That is why `SpanScope.name` is settable at all. OpenTelemetry has the same operation for the same
reason.

A request that matched nothing is never renamed, and keeps its path.

## What it records, and one thing it cannot

| | |
| --- | --- |
| `http.request.method`, `url.path` | At the start |
| `http.route` | After routing matched, when it did |
| `http.response.status_code` | At the end, when there is a response |
| Status `Error` | A 5xx, or a failure that propagated |
| Status `Ok` | A 4xx — a caller being told no is the service working, and colouring both red makes a dashboard useless |

A request whose handler **threw** has no status code on its span. The span is written while the
exception is unwinding, and a `StatusPages` handler produces the response further out, after the
span is gone. The failure itself is on the span, which is the part worth having; a spec pins both
halves of that so it stays a documented shape rather than a surprise.

## Why `Observability` and not `Telemetry`

`Telemetry` is the class this plugin builds, and a file that installs the plugin usually wants the
type too — `val telemetry: Telemetry = application.telemetry`. Two identifiers spelled the same in
one file is a clash the application has to work around, so the plugin took the other word: telemetry
is the data, observability is what an application switches on.

## The two lifecycle rules this repository already had

**`instance` is used and not closed.** A telemetry built elsewhere — by a DI container, or shared
with a worker — belongs to whoever built it. Without one the plugin builds its own from the settings
and closes it on `ApplicationStopped`, which drains the queue. That is `stx-ktor`'s `resource`
helper, unchanged.

**The telemetry goes into Ktor's DI, and that is not a flag.** Installing the plugin registers it
with the container, for the rare class that wants the root itself — to read its resource, or to
close it deliberately. Most need none of that: `install = true` (the default) makes `logger<T>()`
and `span { }` find the telemetry three layers below a route, with no `ApplicationCall` in sight.

## `traced`, for the health check

The default traces everything. A health check answered every second by a load balancer is a trace
nobody will ever read and most of the traces there are:

```kotlin
install(Observability) {
    service = "checkout"
    traced = { it.request.local.uri != "/health" }
}
```

A request the filter refuses runs normally and produces nothing.

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
