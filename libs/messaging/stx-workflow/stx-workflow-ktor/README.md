# stx-workflow-ktor

The Ktor plugin for [`stx-workflow`](../stx-workflow/README.md): one engine per application, and the
worker on the application's own scope.

**`io.github.softistx:stx-workflow-ktor`** — [how to depend on it](../../../../docs/consuming.md).

```kotlin
install(Workflows) {
    store = RedisWorkflowStore(application.redis)
    register(checkout)
    worker = true
}

post("/checkout") { call.respond(call.workflows.start(checkout, call.receive())) }
post("/checkout/{id}/approve") { call.workflows.signal(call.parameters["id"]!!, APPROVAL, call.receive()) }
```

`call.workflows` and `Application.workflows` reach the engine; installing the plugin also registers
it with Ktor's DI, so a class the container builds takes a `WorkflowEngine` in its constructor.

## Why this is a module and not a package in stx-ktor

Because this plugin has a design of its own: an engine to publish, a worker whose lifetime is the
application's, and a store somebody else opened. That is the line — not size. An integration that is
only the seam's idiom applied to one more type stays in the seam, which is why `install(RedisConnection)`
is still a package in `stx-ktor` and this is not.

Beside the library instead, and not *inside* it: `stx-workflow` has callers with no web framework at
all, and a library that declared Ktor in its manifest would carry one to all of them.

What stays in `stx-ktor` is the part that belongs to no integration: `own`, `publish`, `resource` and
`required` — the resource-lifecycle idiom every plugin here shares. Those four were `internal` while
every plugin lived in that module. **This module is why they are public**: they are the contract
between the seam and the integrations built on it, and a contract cannot be internal.

## It opens nothing, and closes nothing

The plugin takes a `WorkflowStore` that already has a connection — `RedisWorkflowStore(application.redis)`
shares what `install(RedisConnection)` opened — because a second pool for the same server is one
nobody asked for. And an engine owns neither the store nor the connection beneath it, which is why
this is the one plugin in this family with no `AutoCloseable` to hand over.

## worker = false is the default

Installing the plugin gives an application a way to *run* workflows; enlisting it in recovering every
abandoned instance in the fleet is a separate question, answered by whoever is shaping the fleet.

When it is on, the worker runs on the application's own scope and is closed with it — which is the
half an application writing `WorkflowWorker(engine).start(this)` by hand tends to forget, leaving a
loop that outlives the redeploy it should have died with.

## A workflow context is @Serializable by definition

So a module installing this plugin needs `settings.kotlin.serialization: json` in its own
`module.yaml`. Nothing in `src/` here is serializable; the setting is the one the *caller* needs, and
forgetting it is a `SerializationException` at the first `start`.

---

Apache-2.0 · [Contributing](../../../../CONTRIBUTING.md) · [All the libraries](../../../../README.md)
