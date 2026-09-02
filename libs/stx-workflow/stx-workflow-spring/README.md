# stx-workflow-spring

Spring Boot auto-configuration for [`stx-workflow`](../stx-workflow/README.md). One engine for the
application, over the store `stx.workflow.store` names, with every `Workflow` bean registered on it.

## An application that uses it

The same checkout that stops for a human approval and delegates delivery to a child workflow — the
declaration itself is in [`docs/workflow.md`](../../../docs/workflow.md#the-whole-thing).

```yaml
stx:
  redis:
    enabled: true
    uri: redis://localhost:6379
    namespace: orders
  workflow:
    enabled: true
    store: redis          # or jpa, or mongo
    lease: 30s
    retention: 7d
    child-poll: 1m
    worker:
      enabled: true
```

```kotlin
@Configuration
class CheckoutWorkflows {
    @Bean
    fun fulfilment(courier: Courier): Workflow<Fulfilment> = fulfilmentWorkflow(courier)

    @Bean
    fun checkout(
        warehouse: Warehouse,
        payments: Payments,
        orders: Orders,
        fulfilment: Workflow<Fulfilment>,
    ): Workflow<Order> = checkoutWorkflow(warehouse, payments, orders, fulfilment)
}

@RestController
class Checkouts(
    private val workflows: WorkflowEngine,
    private val checkout: Workflow<Order>,
) {
    @PostMapping("/checkout")
    suspend fun start(@RequestBody order: Order) =
        workflows.start(checkout, order, id = "checkout:${order.id}")

    @PostMapping("/checkout/{id}/approve")
    suspend fun approve(@PathVariable id: String, @RequestBody approved: Approved) =
        workflows.signal("checkout:$id", APPROVAL, approved).status
}
```

Both workflows are registered because both are beans — the child is not mentioned twice anywhere.
The id is chosen rather than generated so the approval route can reconstruct it, and the courier's
webhook addresses the child as `"checkout:$id/fulfil"`, which is the parent's id plus the node's
path. `start` returns at the first park, so `POST /checkout` answers with an `Awaiting` instance
rather than holding the request until the delivery is collected.

An annotated declaration is a bean the same way — `workflowOf(CheckoutWorkflow(stock, payments))`
returns an ordinary `Workflow<C>`, and nothing downstream can tell the two front ends apart.

[`docs/spring-configuration.md`](../../../docs/spring-configuration.md) has every key.

## Why this is a module and not a package in stx-spring-boot

Because this integration has a design of its own: it chooses between three stores, runs a
`SmartLifecycle`, and owns a configuration group. That is the line — not size. An integration that is
only the seam's idiom applied to one more type stays in the seam, which is why `stx.redis` is still a
package in `stx-spring-boot` and this is not.

Beside the library instead, and not *inside* it: `stx-workflow` has callers with no web framework at
all — a worker, a consumer, a CLI — and a library that declared Spring in its manifest would carry a
web framework to all of them. The library knows workflows, this knows Spring, and neither knows two.

`stx-spring-boot` keeps what belongs to no library: error handling, CORS, security, the JSON and web
conventions. It is the seam, not the switchboard.

## Every Workflow bean is registered

That is the whole wiring and the part that had to be right. An instance is stored under its
workflow's **name**, so an engine that cannot look that name up cannot resume it after a restart, and
a process that registered half the fleet's workflows fails on the other half. Registering by existing
as a bean means there is no second list to keep in step.

## Nothing is inferred about where instances live

`stx.workflow.store` names one of the three stores in `stx-workflow-db`, built over the connection
the matching `stx.*` group already opened. Leave it unset and the application declares its own
`WorkflowStore` bean, which `@ConditionalOnMissingBean` steps aside for.

An application with both a `Redis` and a `Jpa` bean is not saying where its workflow instances
belong. Guessing would put them somewhere plausible and wrong, and nobody would find out until the
cache was flushed — so naming a store whose connection bean is absent fails the context at refresh
rather than at the first workflow.

`store: jpa` needs `com.softistx.workflow.jpa` in `stx.jpa.packages`, or the session factory has no
`WorkflowInstanceRow` to map. Nothing here can fix that: the factory is built before this
configuration, from a list only the application has.

## The worker is a SmartLifecycle

Started after the context is refreshed and stopped before it is torn down, on a scope of its own. A
worker started in an `@PostConstruct` would resume instances against half-built collaborators; one
that outlived the context would resume them against closing ones. It is off unless
`stx.workflow.worker.enabled` says otherwise — running workflows and recovering the fleet's abandoned
ones are different jobs, and usually different pods.

## No admin endpoint

`engine.find(WorkflowStatus.Failed)` is the operator's inbox, and there is deliberately no route here
that exposes it. A route that lists instances and restarts them is exactly the route that must not be
open, and who may call it is a question about your application. `docs/workflow.md` has the four
lines; put them behind whatever your other admin routes are behind.
