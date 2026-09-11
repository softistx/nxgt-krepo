# stx-workflow-spring

Spring Boot auto-configuration for [`stx-workflow`](../stx-workflow/README.md). One engine for the
application, over the store `stx.workflow.store` names, with every `Workflow` bean registered on it.

**`io.github.softistx:stx-workflow-spring`** — [how to depend on it](../../../docs/consuming.md).

```yaml
stx:
  redis: { enabled: true, uri: redis://localhost:6379, namespace: orders }
  workflow:
    enabled: true
    store: redis          # or jpa, or mongo
    worker: { enabled: true }
```

```kotlin
@Bean fun checkout(stock: Stock, payments: Payments): Workflow<Checkout> =
    workflowOf(CheckoutWorkflow(stock, payments))
```

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

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
