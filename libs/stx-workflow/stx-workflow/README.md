# stx-workflow

Workflows for a Kotlin coroutine service: an ordered declaration of steps, a compensation per step,
and state written down after each one so a process that dies mid-run can be picked up where it
stopped.

It is a plain `jvm/lib` with no store in it. The engine talks to a `WorkflowStore`, and which one it
is gets decided once, by the application — `stx-workflow-redis` is the one that ships with this
phase, and `InMemoryStore` is here for tests and for a worker that has nothing to survive.

```kotlin
@Serializable
data class Checkout(
    val items: List<Item>,
    val card: Card,
    val reservationId: String? = null,
    val chargeId: String? = null,
)

val checkout = workflow<Checkout>("checkout") {
    step("reserve") { context.copy(reservationId = stock.reserve(context.items)) }
        .compensate { stock.release(context.reservationId!!) }

    step("charge") { context.copy(chargeId = payments.charge(context.card, key = idempotencyKey)) }
        .compensate { payments.refund(context.chargeId!!) }

    step("confirm") { orders.confirm(context.reservationId!!); context }
}

val engine = WorkflowEngine(RedisWorkflowStore(redis)) { register(checkout) }
val instance = engine.start(checkout, Checkout(items, card))
```

## Shape

```
com.strange.workflow          Workflow, WorkflowEngine, WorkflowWorker, WorkflowInstance, WorkflowStatus
com.strange.workflow.dsl      the verbs — step, branch, parallel, await, sleep, retry, timeout, compensate
com.strange.workflow.annotation  the same declaration as annotations on a class, read by workflowOf
com.strange.workflow.engine   the loop: one attempt, the walk, the unwind
com.strange.workflow.store    WorkflowStore, the persisted record and journal, InMemoryStore
```

## Checkpointed, not replayed

There are two ways to make a workflow survive a restart. One is to re-run the workflow function from
the top and feed each step its recorded result instead of running it — Temporal's model. It buys
arbitrary imperative control flow, and it charges for it in determinism: `now()`, `random()`, a
`Map` iteration order and an `if` on a field that has since changed are all bugs, and they are bugs
that show up days later on a resume rather than in the test that ran the code once.

This library takes the other one. The declaration is data — an ordered list of nodes — and the engine
walks it, writing down what happened after each one. Nothing constrains what a step body does,
because a step body is never re-run for bookkeeping: it is run when it has not been done, and
skipped when it has. What it costs is that control flow has to be *said* rather than written — a
fork is `branch { }` and not an `if` — and that is the trade this repo wants.

## Delivery is at-least-once

**The checkpoint is written after the effect.** A process that dies between a step's effect and its
checkpoint replays that step when the instance resumes, because from the outside those two are
indistinguishable from a process that died during the effect.

That is not a gap to be closed. Writing the checkpoint *before* the effect would give at-most-once
and lose work — a step that never ran costs the order — and there is no third option over arbitrary
user code. So: **a step body must be idempotent, or must have a compensation that tolerates being
asked to undo something that was never done.** The `StepScope` a step runs in exists to make the
first one writable: `idempotencyKey` is the same string on every replay of the same node of the same
instance, and different for every other one — which is exactly what a payment provider's
idempotency key asks for. `idempotencyKey("tip")` is the same thing for a step that makes more than
one call, because giving two calls one key is how a provider is told to skip the second.

The same is true one layer down, and for the same reason. A `RedisLock` is a lock on one Redis, not
a consensus across several; a failover to a replica that had not seen it hands the instance to two
runners. The conclusion is the sentence above, again.

## One typed context, and where it does not fit

A workflow's state is one `@Serializable` type threaded through every step. It makes the persisted
form obvious, the resume exact, and every step's input and output the same thing, which is what lets
a step be skipped without anybody having to reason about what it would have produced.

Two rules come out of it being persisted, and both are review's job rather than a runtime check:

- **Every field added to it later needs a default.** An instance written by the previous deploy has
  to decode under the next one, or a rolling deploy becomes a fleet half of which cannot resume the
  other half's work. The engine's `Json` is lenient about unknown keys for the same reason, so
  *removing* a field is already safe. A context that will not decode does not crash-loop a worker:
  the instance is marked `Failed`, leaves the runnable index, and waits for somebody.
- **It holds references to large things, never the things.** The whole context is written again
  after every node, so a document belongs in it as a storage key — `stx-storage` is right there —
  and not as bytes.

And one place a single typed context genuinely cannot express what is happening: **a fan-out**. Two
legs cannot both return `C`, and merging two `C`s by field is either reflection or last-writer-wins,
both of which are wrong quietly. So each leg produces a value of its own type, named by an
`outcome<T>` key, and a `merge` — ordinary Kotlin, checked by the compiler — folds them in:

```kotlin
private val CHARGE = outcome<String>("charge")
private val COURIER = outcome<Booking>("courier")

parallel("provision") {
    branch(CHARGE) { payments.charge(context.card) }.compensate { id -> payments.refund(id) }
    branch(COURIER) { courier.book(context.items) }.compensate { b -> courier.cancel(b.id) }

    merge { out -> context.copy(chargeId = out[CHARGE], booking = out[COURIER]) }
}
```

Each leg is checkpointed on its own, so an instance that died with the charge through and the courier
still in flight comes back and books the courier without charging twice.

## A leg whose sibling fails is not cancelled

It is allowed to finish, and if it succeeds it is recorded and then compensated like anything else.
Cancelling it would stop it in the middle of a remote call whose result nobody saw — an effect that
is neither written down nor undoable, which is the one state a compensating engine has no answer
for. Waiting costs the time of the slowest leg, once.

## The journal is the only record of progress

There is no cursor. A cursor would be a second answer to the same question, and the two disagree the
moment a node is nested — an index into the top-level list cannot say which of a branch arm's own
steps have run. So a node is skipped when its **last** journal entry says it succeeded, and that one
rule works at every level.

It is also why a `branch` writes down which arm it took **before** running it. A fork re-decided on
resume, against a context its own steps have since changed, sends the engine down the other arm and
leaves it unwinding through steps that never happened.

## A failed compensation stops everything

The unwind runs newest first over the nodes whose last entry says they succeeded, checkpointing each
one, so an interrupted unwind resumes rather than refunding twice. When a compensation fails for
good, the instance lands `Failed` with the node named and **nothing further is undone**. Releasing
the reservation for an order that is still charged leaves a state nobody can describe; a person is
the right answer, and `Failed` is how this says so.

`Compensated` is the ordinary outcome of a workflow that failed. `Failed` is the one that needs
somebody, and it is the only status exempt from the store's retention.

## Resume is not a worker

`engine.resume(id)` is for an operator, a test, or an application with a scheduler of its own.
Anybody who wants instances picked up automatically after a crash wants `WorkflowWorker`; `resume` in
a `while (true)` loop is that class, written again and worse. It is here rather than beside a store
because it asks the engine what is due and resumes it, and knows nothing else.

## A wait is a record, not a coroutine

`await(APPROVAL)` and `sleep("cool-off", 7.days)` both stop the instance by **writing it down** and
letting go: status `Awaiting` or `Sleeping`, the signal's name or the wake-up time in the record, and
the process free to exit.

The alternative is what a suspending function does naturally — park a coroutine and hold it — and it
is wrong here for a reason that has nothing to do with efficiency. A refund waiting four days for an
operator to click approve is not a four-day computation. Written as a `delay` it becomes a four-day
uptime requirement, and Thursday's deploy silently loses every instance mid-wait. Written down, the
process that parked it and the process that finishes it need not overlap at all — which is exactly
what `WorkflowPauseTest` demonstrates, with two engines and nothing between them but Redis.

Two consequences worth stating:

- **A wait with no deadline leaves the due-time index.** It is alive and unfinished and no worker
  polls it, because nothing a worker can do would move it. Polling it would be a loop with itself.
- **A wait with a deadline that passes is a failure.** The workflow unwinds, exactly as it would for
  a step that threw. That is what the deadline is for: the hold placed before the approval must be
  released when the approval never comes. An expiry that quietly took another path would be a
  workflow whose outcome depends on a timer nobody reads.

## Two front ends, one workflow

`workflowOf<C>(CheckoutWorkflow(stock, payments))` reads a class of annotated functions and returns
the same `Workflow<C>` that `workflow<C>("checkout") { }` returns — built by the same builder,
through the same public verbs, with the same checks.

That is the whole design of the annotation front end, and the reason it needed no hook to be added
for it: the DSL verbs are thin extensions over one `NodeSink.add`, so a reflective reader is just
another caller. Nothing downstream can tell the two apart, which means an application can use both,
and a workflow can move from one to the other without touching a stored instance.

The annotations cover the linear vocabulary — steps, compensations, retry, timeout, waits. They do
**not** cover branches or fan-out, and that is deliberate rather than unfinished: a condition is a
predicate and a merge is a function of several typed results, and neither survives being written as
a string. A workflow that needs either is written with `workflow { }`.

## What this slice does not do

**No Ktor or Spring integration**, and no store but Redis and memory. All of them are sibling
modules when they come, and none of them changes anything here — which is the same claim
`stx-workflow-redis` already makes good on, and the reason `WorkflowStore` has five methods.
