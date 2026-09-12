# What a stx-workflow declaration may say

The vocabulary of [`stx-workflow`](../libs/messaging/stx-workflow/stx-workflow/README.md): every verb, what it
takes, and what the engine does with it. The module README explains *why* the library is shaped this
way; this file is what you may write.

- [Declaring a workflow](#declaring-a-workflow)
- [Steps](#steps)
- [Compensation](#compensation)
- [Retry](#retry)
- [Timeout](#timeout)
- [Waiting for a signal](#waiting-for-a-signal)
- [Waiting for a clock](#waiting-for-a-clock)
- [Branches](#branches)
- [Fan-out](#fan-out)
- [The step scope](#the-step-scope)
- [Annotations](#annotations)
- [Running one](#running-one)
- [Status](#status)
- [The persisted record](#the-persisted-record)
- [Changing a declaration that has instances in flight](#changing-a-declaration-that-has-instances-in-flight)
- [Stores](#stores)
- [In a Ktor application](#in-a-ktor-application)
- [In a Spring Boot application](#in-a-spring-boot-application)
- [The worker](#the-worker)

## Declaring a workflow

```kotlin
inline fun <reified C> workflow(name: String, block: WorkflowBuilder<C>.() -> Unit): Workflow<C>
fun <C> workflow(name: String, serializer: KSerializer<C>, block: WorkflowBuilder<C>.() -> Unit): Workflow<C>
```

`C` is the context: one `@Serializable` type threaded through every node, and the whole of what
survives a restart.

`name` is what the store records and what an engine looks a definition up by, so it has to be stable
across deploys — renaming a workflow orphans every instance of it still in flight. **Node names are
the same kind of promise**: the journal is keyed on them, so renaming a node makes a resumed instance
believe it never ran, and it is half of every `idempotencyKey`.

A `Workflow<C>` holds no state and runs nothing. Build one at startup and hand it to an engine, which
runs many instances of it; a step body closes over its collaborators, never over anything belonging
to one run.

**Two rules for a context**, both review's job rather than a runtime check:

| Rule | Why |
| --- | --- |
| Every field added later needs a **default** | An instance written by the previous deploy has to decode under the next one. Removing a field is already safe — the engine's `Json` is lenient about unknown keys |
| It holds **references** to large things, never the things | The whole context is written again after every node. A document belongs in it as a storage key, not as bytes |

A context that will not decode does not crash-loop a worker: the instance is marked `Failed`, leaves
the runnable index, and waits for somebody.

**Every node name must be unique within the workflow**, checked when it is built. The journal is keyed
on names, and two nodes called `charge` would make "has this run?" unanswerable. Names nest: a leg of
the `provision` fan-out is `provision/charge`, and a step in the `express` arm of the `delivery`
branch is `delivery/express/notify`.

## Steps

```kotlin
fun <C> NodeSink<C>.step(name: String, body: suspend StepScope<C>.() -> C): Step<C>
```

A step reads the context and returns the next one. It is the only node that writes the context
directly — a branch chooses, a fan-out merges.

```kotlin
step("reserve") { context.copy(reservationId = stock.reserve(context.items)) }
```

`NodeSink<C>` is the receiver every verb hangs off, so the same vocabulary works at the top level of
a workflow and inside a branch arm.

## Compensation

```kotlin
infix fun <C> Step<C>.compensate(block: suspend StepScope<C>.() -> Unit): Step<C>
```

```kotlin
step("reserve") { context.copy(reservationId = stock.reserve(context.items)) }
    .compensate { stock.release(context.reservationId!!) }
```

Runs when a **later** node fails and the workflow unwinds. The unwind goes newest first over the
nodes whose last journal entry says they succeeded; a node that failed has nothing to take back, and
one that was never reached has nothing either.

**The context a compensation sees is the last one checkpointed**, not a snapshot from when its own
step finished. In the ordinary case, where steps only add to the context, that is strictly more
information. A step that *removes* what an earlier step's compensation needs has broken it.

A compensation is retried under **its own node's** retry policy: `retry { times = 3 }` on a charge
says the same about the refund.

**A compensation that fails for good stops the unwind.** The instance lands `Failed` with the node
named, and nothing before it is undone. See [Status](#status).

## Retry

```kotlin
infix fun <T> T.retry(block: RetryBuilder.() -> Unit): T   // on Step, Leg or Parallel
infix fun <T> T.retry(policy: RetryPolicy): T
```

```kotlin
step("charge") { … } retry {
    times = 3                                              // total attempts, including the first
    backoff = exponential(100.milliseconds, factor = 2.0, max = 5.seconds)
    unless { it is InsufficientFunds }                     // these failures are final
}
```

| Knob | Default | Means |
| --- | --- | --- |
| `times` | `3` | Total attempts, including the first. `1` is no retry |
| `backoff` | `fixed(100.milliseconds)` | How long before attempt *n* |
| `unless(predicate)` | nothing is final | Failures matching it end the node on the spot |

**The default for a node that says nothing is `RetryPolicy.once` — one attempt.** Retrying is a
decision with a cost (a step that is not idempotent, tried twice, charges twice), so it is asked for
rather than assumed.

Two backoffs ship:

```kotlin
fun fixed(delay: Duration): Backoff
fun exponential(initial: Duration, factor: Double = 2.0, max: Duration = 30.seconds): Backoff
```

`max` is not optional in practice: without it the fifth retry of a step with a one-second initial
delay waits sixteen.

`Backoff` is a `fun interface` — `Backoff { attempt -> … }` is a whole custom schedule.

**Two ways to say "do not try this again"**, because they live in different places:

- `unless { }` — the *policy*'s view that a class of failure is permanent.
- `throw NonRetryableException(…)` from the step — only the code that hit the failure knows.

A `CancellationException` is never retried and never recorded. It means the process is going away,
and the instance is left exactly as it is for whoever resumes it.

## Timeout

```kotlin
infix fun <T> T.timeout(duration: Duration): T             // on Step, Leg or Parallel
```

**The clock covers one attempt, not the node.** A step with three attempts and a ten-second timeout
may take thirty seconds plus its backoff. Timing the node as a whole would mean each retry inheriting
what the last one had already spent, so the final attempt — the one that matters — is always given
the least time.

A timeout that fires is an ordinary node failure: it retries if the policy has attempts left, and
otherwise starts the unwind.

## Waiting for a signal

```kotlin
inline fun <reified T> signal(name: String): Signal<T>
fun <T> signal(name: String, serializer: KSerializer<T>): Signal<T>

fun <C, T> NodeSink<C>.await(
    signal: Signal<T>,
    name: String = signal.name,
    body: suspend StepScope<C>.(T) -> C,
): Await<C, T>

infix fun <C, T> Await<C, T>.within(deadline: Duration): Await<C, T>
```

```kotlin
private val APPROVAL = signal<Approval>("approval")

workflow<Refund>("refund") {
    step("hold") { context.copy(holdId = ledger.hold(context.amount)) }
        .compensate { ledger.release(context.holdId!!) }

    await(APPROVAL) { approval -> context.copy(approvedBy = approval.by) } within 24.hours

    step("pay") { context.copy(paymentId = ledger.pay(context.holdId!!)) }
}
```

An `await` stops the instance. Its status becomes `Awaiting`, `awaiting` holds the signal's name, and
nothing after it runs until somebody calls `engine.signal(id, APPROVAL, payload)` — which may be days
later, from a process that did not exist when the instance parked.

The block runs **once**, when the payload arrives, in whichever process delivered it. It is handed
the payload and returns the next context, exactly like a step.

**A payload may arrive before the wait does.** A provider handed a callback URL often calls back
before the step that asked it to has returned, and a workflow that waits on two things in sequence
hears about the second while it is still parked on the first. Both are accepted: a delivery is
written to the record as soon as it is taken, filed under the name of the signal it belongs to, and
the `await` reads whichever payload is addressed to it whenever it gets there. An early delivery does
**not** wake an instance parked on a different signal — that would step over a wait that has not been
answered.

Only the last payload under a name survives, which is what "the approval" means: a second one
overwrites the first rather than queueing behind it.

A `Signal<T>` is a key, not a name, for the same reason an `Outcome<T>` is: the payload is persisted
before the workflow reads it, so something has to hold its serializer, and a bare string would leave
every delivering call site guessing the type.

**A deadline is a failure, not a branch.** `within 24.hours` means the wait fails with
`AwaitTimeoutException` when it expires, and the workflow unwinds through everything before it. That
is what the deadline is *for* — the hold this refund placed must be released if nobody ever approves
it — and it is a rule an operator can read off the status without knowing the engine.

**A wait with no deadline waits forever, and costs nothing while it does.** Such an instance leaves
the due-time index entirely (`WorkflowRecord.isParked`), so no worker polls it. This is not an
optimisation: a worker that polled it would spend its life offering the same instance to itself,
finding the same signal still absent, and parking it again.

An await declares no compensation. Un-approving something is not an effect this library performed;
what the approval *caused* is the steps after it, and those compensate themselves.

A workflow that waits on the same signal twice must name the two waits apart — `await(APPROVAL,
name = "second-approval") { … }` — and the duplicate-name check says so when the workflow is built.

### Webhooks and callbacks

A callback has to find the instance it belongs to, and that needs **no code here**: `start` takes the
id, so choose one the callback can reconstruct.

```kotlin
private val SETTLED = signal<Settlement>("settled")

val payout = workflow<Payout>("payout") {
    step("submit") {
        // instanceId is on the step scope, which is what makes it available as the provider's own
        // reference — and it is already the idempotency key this step needs.
        provider.submit(context.amount, reference = instanceId, key = "$instanceId:$stepName")
        context
    }.compensate { provider.recall(instanceId) }

    await(SETTLED) { settlement -> context.copy(settledAt = settlement.at) } within 3.days
}

// engine.start(payout, Payout(amount), id = "payout:${order.id}")
```

```kotlin
post("/hooks/provider") {
    val event = provider.verify(call.receiveText(), call.request.headers)   // authenticate first
    engine.signal(event.reference, SETTLED, Settlement(event.at))
    call.respond(HttpStatusCode.OK)
}
```

The instance need not have parked on the `await` yet — providers routinely call back before `submit`
returns — so nothing here has to order the two.

What the handler answers matters, because it is what decides whether the provider tries again:

| Outcome | Answer | Why |
| --- | --- | --- |
| `signal` returns | `200` | Delivered and checkpointed |
| `WorkflowNotAwaitingException` | `200` | The instance finished; a retry would never land, and asking for one wastes both sides' time |
| `WorkflowNotFoundException` | `404` | The id is wrong, or the instance was purged |
| `WorkflowUnknownSignalException` | `400` | A name this workflow has no `await` for — a deploy mismatch, not something a retry fixes |
| `WorkflowConflictException` | `409` or `503` | Nothing was written; this is the one the provider **should** retry |

This library verifies nothing about the caller. A webhook route is a public endpoint and the
signature check belongs in front of `signal`, not behind it.

## Waiting for a clock

```kotlin
fun <C> NodeSink<C>.sleep(name: String, duration: Duration): Sleep<C>
fun <C> NodeSink<C>.sleep(name: String, duration: StepScope<C>.() -> Duration): Sleep<C>
```

```kotlin
sleep("cool-off", 10.minutes)
sleep("back-off") { context.retryAfter }
```

`sleep` is not `delay`. A `delay` holds a coroutine, and a coroutine lives in a process that will be
redeployed on Thursday; a seven-day cool-off written that way is a seven-day uptime requirement.
`sleep` writes the wake-up time to the store and lets the instance go — status `Sleeping`, `wakeAt`
set — so the process it started in is free to die and whichever one finds it later finishes it.

It follows that **a sleep needs somebody to come back for it**: a `WorkflowWorker`, or an application
scheduler calling `resume`. Nothing wakes an instance nobody is polling for.

The block form is evaluated **once**, when the instance parks. A resume reads back the `wakeAt` it
wrote rather than asking again — otherwise every restart would push the wake-up further out, and a
restart loop would produce an instance that never wakes. A duration of zero or less is a wake-up
already due, which the engine treats as no pause at all rather than as an error.

## Child workflows

```kotlin
fun <C, D> NodeSink<C>.child(
    name: String,
    workflow: Workflow<D>,
    with: StepScope<C>.() -> D,
    body: suspend StepScope<C>.(D) -> C,
): Child<C, D>

infix fun <C, D> Child<C, D>.within(deadline: Duration): Child<C, D>
```

```kotlin
val fulfilment = workflow<Fulfilment>("fulfilment") {
    step("book") { context.copy(booking = courier.book(context.items)) }
        .compensate { courier.cancel(context.booking!!) }
    await(COLLECTED) { collected -> context.copy(collectedAt = collected.at) } within 2.days
}

val ordering = workflow<Order>("ordering") {
    step("reserve") { context.copy(reservationId = stock.reserve(context.items)) }
        .compensate { stock.release(context.reservationId!!) }

    child("fulfil", fulfilment, with = { Fulfilment(items = context.items) }) { fulfilled ->
        context.copy(trackingId = fulfilled.booking)
    } within 3.days

    step("confirm") { orders.confirm(context.reservationId!!); context }
}
```

Both must be registered with the same engine.

**The child is an instance, not a subroutine.** Its own record, its own journal, its own
compensations. That is the point: a fulfilment that parks two days on a courier's callback cannot be
a function call inside the parent's step, because the parent's step would have to stay in memory for
two days.

`with` builds the child's starting context and runs once. `body` receives the child's **final
context** and returns the parent's next one — the same shape as `await`, because a child that
finished is a payload that arrived.

**Only a `Completed` child feeds `body`.** One that compensated, failed or was cancelled fails this
node with `ChildFailedException`, and the parent unwinds through everything before it — the honest
reading of "the thing I delegated could not be done".

### The id is derived

`"<parent id>/<node path>"` — `order-9/fulfil`. That is what makes the node replayable: a parent that
dies between starting the child and checkpointing that it did comes back, derives the same id, finds
the instance already there and carries on with it rather than starting a second one. It is also what
an operator follows from one record to the other, in either direction: the child's record carries
`parent`.

### Undoing the parent undoes the child

The compensation is implicit and is not `body`'s business: it is `undo` on the child instance, which
runs the child's own compensations in its own reverse order, checkpointed in its own journal.

A child node **owes that compensation from the moment it started the instance**, not from the moment
it succeeded — which is where it differs from every other node. A child still running when its
`within` ran out has failed the parent's node *and* left a real instance behind, so the unwind takes
it back too (`cancel` when it has not finished, `undo` when it has). Without that rule, a slow child
would leak a running instance every time.

A child that is no longer in the store cannot be taken back. That fails the parent's compensation and
the parent lands `Failed`, which is the status that means a person should look.

### Waiting is a park, and it is polled

A parent waiting on a child is `Awaiting`, with `awaiting` holding the **child's id** rather than a
signal name. Unlike a signal wait with no deadline, it is *not* out of the due-time index:

| | Prompt | Correct |
| --- | --- | --- |
| How the parent finds out | The child resumes it the moment it reaches a terminal status | The parent polls the child every `childPoll` |

The two records cannot be written together, so a process that dies between the child's terminal write
and telling anybody about it would leave the parent parked forever on the wake-up alone. The poll is
what covers that, and it needs somebody polling — a `WorkflowWorker`, or an application scheduler
calling `resume`. `childPoll` defaults to one minute and is set on `WorkflowEngine { }`.

## Branches

```kotlin
fun <C> NodeSink<C>.branch(name: String, block: BranchBuilder<C>.() -> Unit)

// inside the block
fun on(name: String, condition: (C) -> Boolean, block: Arm<C>.() -> Unit)
fun otherwise(block: Arm<C>.() -> Unit)
```

```kotlin
branch("delivery") {
    on("express", { it.express }) {
        step("notify-express") { context.also(notifier::expressBooked) }
    }
    otherwise {
        step("notify") { context.also(notifier::booked) }
    }
}
```

The first arm whose condition holds runs; the others do not. `otherwise` is optional, at most one,
and must come last. A branch that matches nothing and has no `otherwise` runs nothing, which is not
a failure — an `if` with no `else` is a legitimate thing to say.

**The condition does not suspend.** A decision that needs a network call is a step that writes what
it learned into the context, followed by a branch on that field — otherwise the decision is invisible
in the journal.

**The arm is chosen once and written down before it runs.** A resume reads it back rather than asking
the conditions again, so a fork stays taken even after a later step changed the field it turned on.
The branch's own journal entry means "this fork has been decided", and its `value` is the arm's name.

Arms are `NodeSink<C>`, so anything a workflow can declare an arm can declare, branches included.

## Fan-out

```kotlin
fun <C> NodeSink<C>.parallel(name: String, block: ParallelBuilder<C>.() -> Unit): Parallel<C>

// inside the block
fun <T> branch(key: Outcome<T>, body: suspend StepScope<C>.() -> T): Leg<C, T>
fun merge(block: suspend StepScope<C>.(Outcomes) -> C)

infix fun <C, T> Leg<C, T>.compensate(block: suspend StepScope<C>.(T) -> Unit): Leg<C, T>

inline fun <reified T> outcome(name: String): Outcome<T>
fun <T> outcome(name: String, serializer: KSerializer<T>): Outcome<T>
```

```kotlin
private val CHARGE = outcome<String>("charge")
private val COURIER = outcome<Booking>("courier")

parallel("provision") {
    branch(CHARGE) { payments.charge(context.card, key = idempotencyKey) }
        .compensate { id -> payments.refund(id) }
    branch(COURIER) { courier.book(context.items) }
        .compensate { booking -> courier.cancel(booking.id) }

    merge { out -> context.copy(chargeId = out[CHARGE], booking = out[COURIER]) }
} retry { times = 3; backoff = exponential(200.milliseconds) }
```

Every leg runs at once. Each produces a value of **its own type**, named by an `outcome<T>` key —
two legs cannot both return `C`, and merging two contexts by field is either reflection or
last-writer-wins. `merge` is ordinary Kotlin, checked by the compiler, and it runs only once every
leg has succeeded. It is not a step: it has no compensation of its own and should not do I/O.

`merge` is required. A fan-out without one does not build.

| Behaviour | |
| --- | --- |
| Checkpointing | **Per leg**, under `<fan-out>/<leg>`. A resume runs only what had not landed |
| A leg's siblings when it fails | **Not cancelled.** They finish, and any that succeed are recorded and then compensated |
| Compensation on failure | Every succeeded leg, newest first, then the failure leaves the fan-out and the steps before it unwind |
| `retry` on the fan-out | Re-runs only the legs that have not succeeded — usually the right place for one |
| `retry` on a leg | That leg's own attempts, inside one pass |
| `timeout` on the fan-out | Covers every leg together |

`Outcomes[key]` throws if the fan-out has no leg of that name.

## The step scope

Every block — a step, a leg, a compensation, a merge — runs with a `StepScope<C>` as its receiver.

| | |
| --- | --- |
| `context: C` | The workflow's context as this node found it |
| `instanceId: String` | This run's id |
| `workflowName: String` | The declaration's name |
| `stepName: String` | The node's **qualified** name — `"provision/charge"` |
| `attempt: Int` | 1 on the first try |
| `startedAt: Instant` | When this attempt began |
| `idempotencyKey: String` | `"<workflow>:<instance>:<node>"` — see below |
| `idempotencyKey(discriminator)` | The same, suffixed, for a node making more than one call |

In a compensation, `attempt` counts that compensation's own attempts.

### Idempotency

Delivery is at-least-once: the checkpoint is written after the effect, so a node whose process died
in between runs again on resume. **`idempotencyKey` is what makes a step's own effects survive that.**
It is the same string on a retry, on a resume, and on a second engine picking the instance up, and
different for every other node, instance and workflow.

```kotlin
step("charge") { context.copy(chargeId = payments.charge(context.card, key = idempotencyKey)) }
```

Three things it does not do, and they are the author's:

- **A node making two calls that both need a key must discriminate them** —
  `idempotencyKey("charge")` and `idempotencyKey("tip")`. The bare key is one string, and giving two
  calls one key is how a provider is told to skip the second.
- **A remote call given no key is not idempotent.** Nothing checks that one was passed.
- **The key is tied to the node's name.** Renaming a step changes the keys of every instance still in
  flight, so a node that has already run somewhere is renamed with the same care as the workflow
  itself — for the same reason, and see [Declaring a workflow](#declaring-a-workflow).

What the **engine** makes idempotent, by contrast, needs nothing from the caller: a node whose
checkpoint landed is never run twice, a compensation already recorded is never repeated, and two
engines cannot both append to one journal — the write is conditional on the record's version.

## Annotations

```kotlin
@Target(CLASS)    annotation class WorkflowDefinition(val name: String)
@Target(FUNCTION) annotation class Step(val order: Int, val name: String = "")
@Target(FUNCTION) annotation class Compensate(val step: String)
@Target(FUNCTION) annotation class Await(val order: Int, val signal: String, val name: String = "", val withinMillis: Long = 0)
@Target(FUNCTION) annotation class Sleep(val order: Int, val name: String = "")
@Target(FUNCTION) annotation class Child(val order: Int, val workflow: String, val name: String = "", val withinMillis: Long = 0)
@Target(FUNCTION) annotation class ChildResult(val child: String)
@Target(FUNCTION) annotation class Retry(val times: Int, val delayMillis: Long = 100, val factor: Double = 1.0, val maxMillis: Long = 30_000)
@Target(FUNCTION) annotation class Timeout(val millis: Long)

inline fun <reified C> workflowOf(definition: Any, vararg children: Workflow<*>): Workflow<C>
```

```kotlin
@WorkflowDefinition("checkout")
class CheckoutWorkflow(private val stock: Stock, private val payments: Payments) {

    @Step(1)
    suspend fun StepScope<Checkout>.reserve(): Checkout =
        context.copy(reservationId = stock.reserve(context.items))

    @Compensate("reserve")
    suspend fun StepScope<Checkout>.releaseReservation() = stock.release(context.reservationId!!)

    @Step(2)
    @Retry(times = 3, delayMillis = 200, factor = 2.0, maxMillis = 5_000)
    @Timeout(millis = 10_000)
    suspend fun StepScope<Checkout>.charge(): Checkout =
        context.copy(chargeId = payments.charge(context.card, key = idempotencyKey))

    @Await(3, signal = "approval", withinMillis = 24 * 60 * 60 * 1000)
    suspend fun StepScope<Checkout>.approved(approval: Approval): Checkout =
        context.copy(approvedBy = approval.by)

    @Sleep(4)
    fun StepScope<Checkout>.settlement(): Duration = 2.days

    @Child(5, workflow = "fulfilment", withinMillis = 3 * 24 * 60 * 60 * 1000)
    fun StepScope<Checkout>.fulfil(): Fulfilment = Fulfilment(items = context.items)

    @ChildResult("fulfil")
    suspend fun StepScope<Checkout>.fulfilled(done: Fulfilment): Checkout =
        context.copy(trackingId = done.booking)
}

val checkout = workflowOf<Checkout>(CheckoutWorkflow(stock, payments), fulfilment)
```

Every annotated function is a **member extension on `StepScope<C>`**, so its body is the same text a
`step { }` block would hold — `context`, `attempt`, `idempotencyKey` all read as they do in the DSL.
The collaborators are the class's constructor, and the instance is built once at startup and shared
by every run, exactly as a `@QueryMapping` instance is in `stx-graphix`.

`workflowOf` returns an ordinary `Workflow<C>`, built by the same builder through the same public
verbs. There is no second engine: an annotated workflow and a written one are indistinguishable to
everything downstream, so the two can coexist in one application and a workflow can move from one to
the other without touching a stored instance.

**`order` is required, and it is not bureaucracy.** `Class.getDeclaredMethods` is documented as
returning methods "in no particular order", so a workflow that took its order from the source would
be one a compiler upgrade could reorder — on a class whose entire contract is that things happen in
sequence. The numbers need not be contiguous; only their relative order is read.

`name` defaults to the function's, on both `@Step` and `@Await`. It is what appears in the journal,
so renaming the function renames the node and an in-flight instance will not recognise it — pin the
name when that matters. The workflow's own name has no default at all, for the same reason and more
so.

**`@Child` is two functions**, for the same reason `@Step` and `@Compensate` are: they run at
different moments — months apart, in the cases a child workflow exists for — and one of them is
handed something the other has never seen. The first computes the child's starting context and does
not suspend, as `@Sleep` computes a duration; the second is handed the child's **final** context and
returns the parent's next one.

It names the child by its **workflow name**, because an annotation cannot hold a `Workflow<D>`, so
the declaration is passed alongside the definition: `workflowOf<Checkout>(CheckoutWorkflow(…),
fulfilment)`. That string is checked against what was passed, and both context types are checked
against the child's serializer — by serial name, which is what actually has to match, since the
child's context is stored encoded and read back with the child's own serializer.

**Everything a class can get wrong is checked when `workflowOf` runs, and reported together**: a
compensation naming no step, two nodes claiming one order, an `@Await` with no payload parameter, a
`@Child` naming a workflow nobody passed, a `@ChildResult` naming no `@Child`, a receiver on the
wrong `StepScope<C>`. Fixing three mistakes should take one run, not three.

### What annotations cannot say

There is no `@Branch` and no `@Parallel`. A branch's condition is a predicate and a fan-out's merge
is a function of several typed results; both are ordinary Kotlin in the DSL, and as annotations they
would be strings or magic method names checked at startup at best. A workflow that needs either is
written with `workflow { }` — which is the whole language, and is what `workflowOf` produces anyway.

`@Retry` has no `unless`, because `unless { }` takes a predicate and an annotation cannot hold one.
An annotated step says "do not try this again" by throwing `NonRetryableException`, which is the half
that knows.


## Running one

```kotlin
fun WorkflowEngine(store: WorkflowStore, block: WorkflowEngineBuilder.() -> Unit = {}): WorkflowEngine

class WorkflowEngineBuilder {
    var json: Json                                   // default: stx-common's lenientJson
    fun register(workflow: Workflow<*>)
}

suspend fun <C> WorkflowEngine.start(workflow: Workflow<C>, context: C, id: String = …): WorkflowInstance<C>
suspend fun <C> WorkflowEngine.startAt(workflow: Workflow<C>, context: C, at: Instant, id: String = …): WorkflowInstance<C>
suspend fun <C> WorkflowEngine.startAfter(workflow: Workflow<C>, context: C, delay: Duration, id: String = …): WorkflowInstance<C>
suspend fun WorkflowEngine.resume(id: String): WorkflowRecord
suspend fun <C> WorkflowEngine.resume(workflow: Workflow<C>, id: String): WorkflowInstance<C>
suspend fun <T> WorkflowEngine.signal(id: String, signal: Signal<T>, payload: T): WorkflowRecord
suspend fun WorkflowEngine.cancel(id: String): WorkflowRecord
suspend fun WorkflowEngine.undo(id: String): WorkflowRecord
suspend fun WorkflowEngine.record(id: String): WorkflowRecord?
suspend fun WorkflowEngine.runnable(now: Instant = …, limit: Int = 32): List<String>
```

`start` **returns when the workflow finishes**, compensates or fails — not when it is submitted. A
caller that wants to hand the work off starts it from a coroutine of its own, or lets a worker pick
it up.

A workflow must be `register`ed before `start` will run it: an instance the engine cannot look up
again is an instance that cannot be resumed after a restart.

### Booking a start for later

`startAt(flow, context, at = booking.startsAt - 24.hours)` creates the instance and runs none of it.
It has an id, a context and a record that `cancel` works on from that moment; what it has **not** got
is a journal, and that is what says it has not begun — every node that runs writes an entry, so
nothing else in this design is `Sleeping` with nothing recorded.

It sits in the store's due-time index scored at `at`, so **something has to come back for it**: a
`WorkflowWorker`, or an application scheduler calling `resume`. An engine with nobody polling has
booked an instance that never starts — the same rule `sleep` lives under, for the same reason.
Nothing here holds a timer.

Resuming it early runs nothing and leaves it booked, because a store may offer an instance a little
early and running it then would make `startAt` mean "about then". An `at` that has already passed is
due, not deferred: it starts now.

**There is no recurrence, and it is not an omission.** "Every night at three" is a schedule, and a
schedule is not an instance: it outlives every run of it, it has to survive being paused and edited,
and it wants a store and a vocabulary of its own. Built out of one instance re-booking the next, it
would be a chain in which one lost link ends the series with nobody noticing.

`signal(id, SIGNAL, payload)` delivers a payload to an instance and runs it on from there in the
calling process. The instance does **not** have to be waiting yet — see [waiting for a
signal](#waiting-for-a-signal). Two deliveries are refused, both loudly:

| Refusal | When | Why not just store it |
| --- | --- | --- |
| `WorkflowUnknownSignalException` | The workflow declares no `await` on that name | Storing it would tell the caller it landed when nothing will ever read it. A typo and a signal renamed on one side only both land here, at the first delivery rather than at the wait that never wakes |
| `WorkflowNotAwaitingException` | The instance has already finished | This is the approval clicked twice, and the second one must not look like it was recorded |

Delivery happens under the instance's lock, so two people approving at the same instant cannot both
wake it. When another process holds that lock, `signal` **waits and tries again** for about four
hundred milliseconds rather than handing back what the store has — a delivery dropped because the
instance happened to be mid-step is the exact failure this is built to rule out. A lock still held
after that throws `WorkflowConflictException`, and nothing was written: an HTTP handler should answer
with something the caller will retry.

`cancel` unwinds first and lands `Cancelled`. A cancelled checkout that leaves the stock reserved is
not cancelled.

`undo` is `cancel`'s counterpart across the finish line: it reverses an instance that already
**succeeded**, landing it `Cancelled`. The two are kept apart on purpose. `cancel` is a safe no-op on
anything terminal and every caller reaching for it defensively depends on that; folding them together
would turn one of those calls into a refund nobody asked for. `undo` is the one you have to mean, and
it refuses everything else — a running instance is `cancel`'s, a `Compensated` or `Cancelled` one has
been unwound already and undoing it twice would take back a compensation, and a `Failed` one is
waiting for a person by design. It is also what a [`child` node's](#child-workflows) compensation
runs.

Asking for an instance somebody else is advancing is **not an error**: the engine takes the
instance's lock, and when it cannot it hands back what the store has rather than queueing.

`resume` is for an operator, a test, or an application with its own scheduler. For automatic
recovery, use [the worker](#the-worker).

## Status

| Status | Means | Terminal |
| --- | --- | --- |
| `Running` | A node is running, or the next one is about to | |
| `Awaiting` | Stopped until a named signal arrives, or until a [child workflow](#child-workflows) finishes — `awaiting` says which | |
| `Sleeping` | Stopped until a point in time — a [`sleep`](#waiting-for-a-clock), or a [start booked for later](#booking-a-start-for-later) that has not come round | |
| `Compensating` | A node failed for good; the journal is being unwound | |
| `Completed` | Every node succeeded | yes |
| `Compensated` | A node failed, and every compensation owed for it succeeded | yes |
| `Failed` | A node failed, **and a compensation failed too** | yes |
| `Cancelled` | Stopped on request; whatever had run was compensated first | yes |

`Compensated` is the ordinary outcome of a workflow that failed. `Failed` is the one that asks for a
person, and it is the only status exempt from a store's retention.

`Awaiting` and `Sleeping` are neither progress nor failure: the workflow stopped on purpose, and it
will stop for as long as that takes. An `Awaiting` instance with no deadline is out of the store's
due-time index and moves only on `signal` or `cancel`; a `Sleeping` one is in the index, scored at
its `wakeAt`.

## The persisted record

```kotlin
@Serializable
data class WorkflowRecord(
    val id: String,
    val workflow: String,
    val status: WorkflowStatus,
    val context: JsonElement,
    val journal: List<JournalEntry> = emptyList(),
    val awaiting: String? = null,       // the signal name, or a child's id, while Awaiting
    val signals: Map<String, JsonElement> = emptyMap(),  // delivered payloads no await has read yet
    val wakeAt: Instant? = null,        // a Sleeping instance's wake-up, or an Awaiting one's deadline
    val error: WorkflowError? = null,
    val cancelled: Boolean = false,
    val parent: String? = null,         // the instance whose `child` node started this one
    val createdAt: Instant,
    val updatedAt: Instant,
    @Transient val version: Long = 0,
)
```

The context is stored **encoded**: a store does not know `C` and has no serializer for it, which is
what lets `WorkflowStore` be one non-generic interface instead of one per workflow.

`version` is **not serialized**. A store keeps it beside the record so a conditional write can compare
it without decoding the document, and so there is only ever one copy of it to keep true.

```kotlin
@Serializable
data class JournalEntry(
    val node: String,                   // qualified — "provision/charge"
    val outcome: NodeOutcome,           // Succeeded | Failed | Paused | Compensated | CompensationFailed
    val attempts: Int,
    val value: JsonElement? = null,     // a leg's result, the arm a branch took, a signal's payload
    val error: WorkflowError? = null,
    val at: Instant,
)
```

**The journal is the only record of progress** — there is no cursor. Entries are appended, never
rewritten, so a node's **last** entry is its current state; everything that asks "has this run?" asks
it that way. `record.latest(node)`, `record.succeeded(node)` and `record.paused(node)` are how.

`Paused` is what an `await` or a `sleep` writes when it stops, and it is why "where is this instance
parked" needs no field of its own to disagree with the journal. It is superseded by the `Succeeded`
entry the same node writes when the wait ends.

A signal's payload is kept in the entry that consumed it. A signal is the one input to a workflow
that came from outside it, and an operator asking six months later why this instance paid out should
not have to find the answer in another system's log.

## Changing a declaration that has instances in flight

A node is matched to its journal entry **by name**, and by nothing else. There is no definition
version and no refusal to resume an instance that older code started, so what a rename or a deletion
does depends entirely on which names the two sides still share.

| Change | What happens to an instance already running |
| --- | --- |
| The body of a step | Nothing. The name still matches, so a node that has run stays skipped and one that has not runs the new body |
| Adding a step | It runs on resume, in its declared position, in the middle of an instance that started before it existed |
| Renaming a step | It runs **again** under the new name, and what it did under the old one is never compensated — the old name is no longer in the declaration's compensations |
| Deleting a step | Its `Succeeded` entry stays in the journal and the unwind steps over it. The effect is never undone |
| Reordering steps | Nothing. The unwind walks the journal in reverse, so it follows what happened rather than what the declaration lists |

The two dangerous rows are asserted in `DefinitionChangeTest`, against an instance interrupted
mid-run and handed to an engine holding the changed declaration.

**`Compensated` means every compensation this declaration owed has run.** After a deletion it does
not mean the instance was fully undone, because the declaration no longer owes the deleted node's.

So renaming or deleting a node while instances are in flight is a data migration, not a refactor.
The two ways through it: keep the old node declared until the old instances have drained, or leave
the name alone and change the body under it.

## Stores

```kotlin
interface WorkflowStore {
    suspend fun create(record: WorkflowRecord)
    suspend fun load(id: String): WorkflowRecord?
    suspend fun save(record: WorkflowRecord, expectedVersion: Long): Boolean
    suspend fun runnable(now: Instant, limit: Int): List<String>
    suspend fun find(status: WorkflowStatus, limit: Int = 50, offset: Int = 0): List<WorkflowRecord>
    suspend fun <T> guarded(id: String, block: suspend () -> T): T?
}
```

Six methods, and that is the whole of what a new backing store has to answer for.

- **`save` must be conditional.** Returning true when the stored version was not `expectedVersion`
  turns a lost race into a lost journal.
- **`guarded` declines, it does not queue.** Null means somebody else holds it; the caller moves on.
  An implementation over a lock with a TTL has to renew it while the block runs.
- **A parked instance is not runnable.** `record.isParked` — `Awaiting` with no `wakeAt` — means
  only a signal will move it, and a store that offered it would put a worker in a loop with itself.
- **`find` is ordered newest first**, by `updatedAt`, and paged with an offset. It takes no filter by
  workflow name: that is a `filter` on a page the caller already has, and indexing a second
  dimension for it would be three different indexes for one convenience.

What ships:

| | |
| --- | --- |
| `InMemoryStore` | In the core module. The reference implementation of the contract — the conditional write really is conditional, `guarded` really excludes. What the engine's own specs run against |
| `RedisWorkflowStore` | In `stx-workflow-db`. Key layout, Lua write, lease, TTL retention |
| `JpaWorkflowStore` | In `stx-workflow-db`. One row per instance, over `stx-jpa`. Postgres, DB2 or MySQL — the URI's scheme picks |
| `MongoWorkflowStore` | In `stx-workflow-db`. One document per instance, with a TTL index doing the retention |

All three persistent stores are described in [the module's README](../libs/messaging/stx-workflow/stx-workflow-db/README.md),
and all three run the same shared contract spec, so the rules above are checked rather than intended.

```kotlin
class RedisWorkflowStore(
    redis: Redis,
    lease: Duration = 30.seconds,
    retention: Duration? = 7.days,
    json: Json = redis.json,
)

class JpaWorkflowStore(
    jpa: Jpa,
    lease: Duration = 30.seconds,
    json: Json = jpa.config.json,
)

suspend fun MongoWorkflowStore(
    database: MongoDatabase,
    collection: String = WORKFLOW_INSTANCES,
    lease: Duration = 30.seconds,
    retention: Duration? = 7.days,
    json: Json = lenientJson,
): MongoWorkflowStore
```

`JpaWorkflowStore` needs `WorkflowInstanceRow` among the entities the application connects with, and
it has no `retention`: a table has no TTL, so retention is `purge(before)` on a schedule the
application owns. `MongoWorkflowStore` suspends because it creates its two indexes before handing
the store back.

### The operator's inbox

```kotlin
val stuck = engine.find(WorkflowStatus.Failed)
stuck.forEach { println("${it.id}: ${it.error?.node} — ${it.error?.message}") }
engine.resume(checkout, stuck.first().id)      // after fixing whatever the compensation choked on
```

`Failed` is the one outcome the engine deliberately refuses to resolve on its own — a compensation
that could not be made to work — and `find` is what makes the status mean something. Without it the
only read is `record(id)`, and an operator does not have the id.

Every store keeps an index for this: a sorted set per status in Redis, a `(status, updated_at)` index
in SQL, a compound index in MongoDB. It is the second thing all three maintain on every write.

**There is no ready-made HTTP endpoint for it, in either integration.** An admin route that lists
instances and restarts them is exactly the route that must not be open, and who may call it is a
question about your application rather than about this library. The four lines above are the route;
put them behind whatever your other admin routes are behind.

## In a Ktor application

```kotlin
install(RedisConnection) { config = RedisConfig(uri = …, namespace = "orders") }
install(Workflows) {
    store = RedisWorkflowStore(application.redis)
    register(checkout)
    worker = true
}

post("/checkout") { call.respond(call.workflows.start(checkout, call.receive())) }
post("/checkout/{id}/approve") { call.workflows.signal(call.parameters["id"]!!, APPROVAL, call.receive()) }
```

`com.softistx.workflow.ktor`, in `stx-workflow-ktor` — a module beside the library rather than a package in `stx-ktor`. `call.workflows` and
`Application.workflows` reach the engine; installing the plugin also registers it with Ktor's DI, so
a class the container builds takes a `WorkflowEngine` in its constructor.

The plugin **does not open a connection**. It takes a `WorkflowStore` that has one —
`RedisWorkflowStore(application.redis)` shares what `install(RedisConnection)` opened — because a
second pool for the same server is one nobody asked for. And nothing here is closed: an engine owns
neither the store nor the connection beneath it, which is why this is the one plugin in that module
with no `AutoCloseable` to hand over.

**`worker = false` is the default, and it is a decision rather than caution.** Installing the plugin
gives an application a way to *run* workflows; enlisting it in recovering every abandoned instance in
the fleet is a separate question, answered by whoever is shaping the fleet. When it is on, the worker
runs on the application's own scope and is closed with it — which is the half an application writing
`WorkflowWorker(engine).start(this)` by hand tends to forget, leaving a loop that outlives the
redeploy it should have died with.

## In a Spring Boot application

```yaml
stx:
  redis:    { enabled: true, uri: redis://localhost:6379, namespace: orders }
  workflow:
    enabled: true
    store: redis          # or jpa, or mongo
    worker: { enabled: true }
```

```kotlin
@Bean fun checkout(stock: Stock, payments: Payments): Workflow<Checkout> =
    workflowOf(CheckoutWorkflow(stock, payments))

@RestController
class Checkouts(private val workflows: WorkflowEngine, private val checkout: Workflow<Checkout>) {
    @PostMapping("/checkout")
    suspend fun start(@RequestBody order: Checkout) = workflows.start(checkout, order)
}
```

`com.softistx.workflow.spring`, in `stx-workflow-spring` — a module beside the library rather than a package in `stx-spring-boot`.
[`docs/spring-configuration.md`](spring-configuration.md) has every key.

**Every `Workflow<*>` bean is registered with the engine.** That is the whole wiring and the part
that had to be right: an instance is stored under its workflow's *name*, so an engine that cannot
look that name up cannot resume it after a restart, and a process that registered half the fleet's
workflows will fail on the other half. Registering by existing as a bean means there is no second
list to keep in step.

Like the Ktor plugin, it opens nothing. `stx.workflow.store` names one of the three stores in
`stx-workflow-db` and it is built over the connection the matching `stx.*` group already opened.
**Nothing is inferred**: an application with both a `Redis` and a `Jpa` bean is not saying where its
workflow instances belong. Leave the key unset and declare a `WorkflowStore` bean instead, and
`@ConditionalOnMissingBean` steps aside for it.

## The worker

```kotlin
class WorkflowWorker(
    engine: WorkflowEngine,
    poll: Duration = 1.seconds,
    batch: Int = 32,
    concurrency: Int = 8,
) : AutoCloseable {
    fun start(scope: CoroutineScope): Job
    override fun close()
}
```

Opt-in by construction: nothing starts one, and putting `stx-workflow` on a classpath starts no
background work.

It lives in the **core** module, not beside a store. It asks the engine what is due and resumes it —
`WorkflowStore.runnable` and `WorkflowStore.guarded`, and nothing else — so the same worker drives
instances in Redis, in Postgres, in MongoDB, or in memory.

It asks the store what is due and calls `resume` on each. There is no claim step — the engine takes
the instance's lock itself and returns quietly when somebody else has it, so two workers pulling the
same id is the normal shape of this rather than a race to prevent. It runs on a `CoroutineScope` the
caller owns and cancels; `close()` stops it and touches neither the engine nor the connection under
it.
