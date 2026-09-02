# stx-workflow-ktor

The Ktor plugin for [`stx-workflow`](../stx-workflow/README.md): one engine per application, and the
worker on the application's own scope.

## An application that uses it

A checkout that stops for a human approval and delegates delivery to a child workflow — the
declaration itself is in [`docs/workflow.md`](../../../docs/workflow.md#the-whole-thing).

```kotlin
fun Application.module() {
    install(RedisConnection) { config = RedisConfig(uri = "redis://localhost:6379", namespace = "orders") }

    install(Workflows) {
        store = RedisWorkflowStore(application.redis)
        register(fulfilment)               // the child, or the parent cannot look it up on resume
        register(checkout)
        worker = true                      // this process also recovers abandoned instances
        injectable = true                  // and hands the engine to Ktor's DI
    }

    routing {
        post("/checkout") {
            val order = call.receive<Order>()
            call.respond(call.workflows.start(checkout, order, id = "checkout:${order.id}"))
        }

        post("/checkout/{id}/approve") {
            call.workflows.signal("checkout:${call.parameters["id"]}", APPROVAL, call.receive<Approved>())
            call.respond(HttpStatusCode.Accepted)
        }

        post("/hooks/courier") {
            val event = courier.verify(call.receiveText(), call.request.headers)   // authenticate first
            call.workflows.signal("${event.reference}/fulfil", COLLECTED, Collected(event.at))
            call.respond(HttpStatusCode.OK)
        }
    }
}
```

Three things in there are load-bearing rather than decorative:

- **`install(Workflows)` comes after the connection plugin.** Ktor runs install blocks in order, and
  `application.redis` throws by name when `install(RedisConnection)` has not happened yet.
- **The id is chosen, not generated.** `"checkout:${order.id}"` is what lets the approval route and
  the courier's webhook find the instance again with nothing persisted to make the link — and the
  child's is that plus the node's path, which is what `${event.reference}/fulfil` addresses.
- **`start` returns at the first park**, not when the run is submitted, so `POST /checkout` answers
  with an `Awaiting` instance rather than holding the request for two days. A declaration with no
  park in it would hold it for the whole run; hand that off to a coroutine of your own.

`call.workflows` and `Application.workflows` reach the engine; `injectable = true` registers it with
Ktor's DI so a class the container builds can take a `WorkflowEngine` in its constructor.

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
