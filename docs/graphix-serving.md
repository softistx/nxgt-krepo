# Serving a stx-graphix schema

The runtime half of the `stx-graphix` vocabulary: schema documents and their location syntax,
`execute`, and the HTTP, SSE and graphql-ws endpoints. The schema half is
[`graphix.md`](graphix.md); the resolver half is [`graphix-resolvers.md`](graphix-resolvers.md).

## Documents

What the *query* may say is graphql-java's, not Graphix's: nothing below needs wiring, an
annotation or a builder call, and `ConformanceTest` is what proves it.

| In a document | Notes |
| --- | --- |
| `@skip(if:)` / `@include(if:)` | The two spec directives. A skipped field is absent from `data`, not null |
| Named fragments, inline fragments | `fragment f on Product { … }`, `... on Product { … }` |
| Aliases | Two aliases of one field with different arguments are two independent fields — and, for `@BatchMapping`, two DataLoader keys |
| Variables, with their own defaults | `query Q($n: String = "ada")`. A variable explicitly `null` on an optional argument falls through to the Kotlin default |
| `operationName` | Which operation runs when the document holds more than one |
| `__typename`, `__schema`, `__type` | Introspection is on by default; `introspection(false)` turns `__schema`/`__type` off, and `__typename` keeps working |

**Not supported.** `@defer` is `@ExperimentalApi` in graphql-java 26: it needs incremental support
switched on, an `IncrementalExecutionResult` path through `execute`, and `multipart/mixed` on both
HTTP plugins. `@stream` is not experimental there — it is **absent**: `graphql.Directives` defines
`defer` and nothing else, and the `StreamedCall` in the incremental package has no directive to
trigger it. Automatic persisted queries, the multipart upload spec and Apollo Federation are outside
graphql-java core and are not wrapped here.

That is why a `Flow` on a query is *collected* rather than streamed. The whole list is built before
the response is written, exactly as for a `List` return, and a client waits the same either way —
which is the opposite of `@stream`, and why accepting a `Flow` there does not reopen incremental
delivery.

## Execute

```kotlin
val result = graphix.execute(
    GraphixRequest(query = query, variables = mapOf("id" to "p1")),
    context = mapOf(Caller::class to caller),
)

graphix.subscribe(GraphixRequest("subscription { productAdded { name } }"))
    .collect { event -> /* one GraphixResult per event */ }
```

`result.data` is the GraphQL data map. `result.errors` is the GraphQL error list. A resolver that
throws becomes an error there; `execute` itself still returns.

A `GraphixError` carries the whole spec shape — `message`, `path`, `locations` (1-based line and
column), `extensions`, and graphql-java's `errorType` classification (`ValidationError`,
`DataFetchingException`, and so on), which is what tells a bad document from a resolver that threw.
`result.extensions` is whatever instrumentation put in the result's, usually empty. Subscription events keep that same
shape, in order (`KEEP_SUBSCRIPTION_EVENTS_ORDERED`).

## HTTP

The JSON envelope is `{ "query", "variables", "operationName", "extensions" }`. The response is
`{ "data", "errors", "extensions" }`, where an error carries `message`, `path`, `locations` and its
own `extensions`. Anything empty is **omitted** rather than sent as `[]` or `{}`. Request
`extensions` are passed through to the operation untouched, where instrumentation can read them. A field error is HTTP **200** with `errors[]`. Malformed JSON, a missing
query, or unparseable GET `variables` is HTTP **400** with `errors[]`.

`GET /graphql?query=...` is for queries only. A **mutation over GET** is refused with HTTP **405**, an
`Allow: POST` header and an `errors[]` body, and it is not executed: a GET is what a link, an image tag or a
prefetch can send cross-site, so a state change must not ride on one. Both integrations do this. A query
over GET, introspection included, runs as before. Variables on GET are a JSON
object in the `variables` query parameter. `__schema` and `__type` are on by default — GraphiQL
and Apollo Sandbox POST the standard introspection query to the same path.

Turning them off is one switch, on the engine or through either integration:

| Where | How |
| --- | --- |
| `Graphix { }` | `introspection(false)` |
| Ktor | `install(GraphQL) { introspection = false }` |
| Spring Boot | `stx.graphix.introspection=false` |

A document that then asks for `__schema` or `__type` comes back as a GraphQL error rather than
data; `__typename` is unaffected, and every other field runs as usual. An engine supplied by the
application (Ktor `instance`, or a `Graphix` bean) already decided for itself and ignores the
setting.

### Apollo Sandbox

`GET /sandbox` serves Apollo's embedded sandbox — a query editor with the schema in a side panel,
against this server. **Off by default**: opening a GraphQL endpoint is what installing the plugin
means, opening an HTML page that advertises the schema is not.

| | Ktor | Spring Boot | Default |
| --- | --- | --- | --- |
| Serve it | `sandbox = true` | `stx.graphix.sandbox=true` | `false` |
| Where | `sandboxPath = "/explorer"` | `stx.graphix.sandbox-path` | `/sandbox` |
| Which endpoint | `sandboxEndpoint = "…"` | `stx.graphix.sandbox-endpoint` | empty — resolved in the browser |

Left empty, the page resolves the endpoint itself: `new URL(<the GraphQL path>, window.location.origin)`.
That is what survives a reverse proxy, https, and a container publishing a port other than the one
the server bound — the host and port the *client* reached are the only ones that are right, and only
the browser knows them. Set `sandbox-endpoint` to an absolute URL to point it somewhere else.

Two things it needs. **Introspection**, since that is how it draws the schema: the page still loads
with `introspection(false)`, the schema panel is just empty. And **the CDN** — the page pulls
`embeddable-sandbox.cdn.apollographql.com`, so a strict `Content-Security-Policy` or an air-gapped
network will block it, and the page renders blank with a console error.

**The sandbox cannot run a subscription under `graphql-ws`.** The page is given an endpoint and
nothing else — it is never told to open a socket — so it POSTs the subscription document and gets
the deliberate 400. Testing one needs a real graphql-ws client, or `sse` for the duration.

A **subscription** is one of two protocols, configurable, default `sse`:

| Protocol | Transport | Config |
| --- | --- | --- |
| `sse` | `text/event-stream` on POST **and** GET, one `data: {json}` frame per event | Ktor `subscriptions = Sse`; Spring `stx.graphix.subscriptions=sse` |
| `graphql-ws` | WebSocket on the same path, sub-protocol `graphql-transport-ws` | Ktor `subscriptions = GraphqlWs`; Spring `stx.graphix.subscriptions=graphql-ws` |

SSE keeps the `{ "data", "errors" }` envelope. Cancelling the HTTP client cancels the `Flow`.
`graphql-ws` speaks `connection_init` / `connection_ack`, `subscribe` / `next` / `complete`,
and `ping` / `pong`. HTTP POST of a subscription document is then 400 — the socket is the
subscription transport. Queries and mutations stay POST/GET, and also run as a single `next`
over the socket.

### The connection_init payload

**A socket carries no `Authorization` header past the handshake.** That is the whole reason the
protocol has a `connection_init` frame, and Graphix puts what the client sent there into the
operation context as `GraphqlWsInit`:

```kotlin
data class GraphqlWsInit(val payload: JsonElement?)
```

A `JsonElement` and not a shape of its own, because the protocol says only that the payload is
arbitrary JSON — `{"authToken": "…"}` is convention, not spec — and `null` when the client sent
none. The core registers the type itself, so nothing calls `contextParameter(...)` for it:

```kotlin
@SubscriptionMapping
fun events(init: GraphqlWsInit): Flow<Event> {
    val token = init.payload?.jsonObject?.get("authToken")?.jsonPrimitive?.content
    …
}
```

It is filled **per operation, not per socket** — the payload is read again on every `subscribe`
frame, next to the interceptor chain that also runs per operation rather than per connection. That
is what lets a credential expiring mid-socket be seen to have expired, instead of a socket keeping
whatever it was authorised with at the handshake.

The rest of a socket operation's context is the **handshake's**: `ApplicationCall` under Ktor,
`ServerWebExchange` under Spring, both from the one HTTP request a socket has, and both frozen at
the upgrade. Anything that changes mid-socket comes through `connection_init` instead.

> **A context type is a hard requirement of the parameter.** A resolver taking `init: GraphqlWsInit`
> works over the socket and *fails everywhere else* — over POST or SSE the type is absent, and the
> field comes back as `no com.softistx.graphix.http.GraphqlWsInit in the operation context` in
> `errors[]`. It is an ordinary field error, so an `errors { }` handler can edit it, but the field is
> still broken on that transport.

So a field reachable from more than one transport reads the payload in an **interceptor**, where
`get<T>()` answers `null` rather than failing, and puts a domain type the resolvers can take:

```kotlin
intercept {
    val token =
        get<GraphqlWsInit>()?.payload?.jsonObject?.get("authToken")?.jsonPrimitive?.content
            ?: call.request.headers["Authorization"]        // GraphixChain.call, from stx-graphix-ktor
    put(Caller(token ?: "anonymous"))
    proceed()
}
```

Resolvers then take `Caller`, which exists on every transport. Note the order: over the socket
`call` is the *handshake's*, whose `Authorization` header a browser cannot set, so `connection_init`
is asked first and the header is the POST/SSE fallback.

To refuse outright, `GraphqlWsClose` carries the protocol's own codes — `UNAUTHORIZED` (4401),
`INIT_TIMEOUT` (4408), `SUBSCRIBER_EXISTS` (4409), `TOO_MANY_INITS` (4429).

**Ktor** — `install(GraphQL)` in `stx-graphix-ktor`, path configurable, default `/graphql`.

**Spring Boot** — `stx.graphix.enabled=true` in `stx-graphix-spring`. Beans annotated
`@GraphQLController` become query/mutation/subscription roots. An application's own `Graphix`
bean wins.

## Schema documents

By default Graphix scans `classpath:graphql/` the way Spring GraphQL does: every
`.graphqls` and `.gqls` file under that directory, nested folders included, is parsed and
**merged** (`extend type Query` is how a file adds fields to a type another file named).
`union` and `interface` declarations need no wiring either: Graphix registers a type resolver for
each, and `typeResolver(name) { }` overrides one.
The documents are the GraphQL schema; `@QueryMapping` / `@SchemaMapping` / `@BatchMapping`
are DataFetchers on those fields. Every built-in scalar is declared and wired
automatically, so a document may use `LocalDate` or `PositiveInt` without a `scalar` line of its
own. A scalar the document or the application defines itself under a built-in's name keeps its own
meaning: the built-in of that name is dropped rather than colliding with it.

No files found: the annotated `@Serializable` types remain the schema, as before.

A `classpath:` location is found wherever the class loader can see it: a directory, a jar, and a
jar nested in another — the `BOOT-INF/classes/` of a Spring Boot executable jar, which is what
`./kotlin package -f executable-jar` writes. Until 0.2.2 the nested case found nothing, and an
application packaged that way silently fell back to its types.

Ktor: `schemaLocations` / `schemaFileExtensions` on `install(GraphQL)`. Spring:
`stx.graphix.schema-locations` and `stx.graphix.schema-file-extensions`. Core:

```kotlin
Graphix {
    schemaLocations("classpath:graphql/", "classpath:extra/")
    resolvers(ProductQueries(store))
}
```
