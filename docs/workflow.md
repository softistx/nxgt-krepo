# What a stx-workflow declaration may say

The vocabulary of [`stx-workflow`](../libs/stx-workflow/stx-workflow/README.md): every verb, what it
takes, and what the engine does with it. The module README explains *why* the library is shaped this
way; this file is what you may write.

- [Declaring a workflow](#declaring-a-workflow)
- [Steps](#steps)
- [Compensation](#compensation)
- [Retry](#retry)
- [Timeout](#timeout)
- [Branches](#branches)
- [Fan-out](#fan-out)
- [The step scope](#the-step-scope)
- [Running one](#running-one)
- [Status](#status)
- [The persisted record](#the-persisted-record)
- [Stores](#stores)
- [The worker](#the-worker)

## Declaring a workflow

```kotlin
inline fun <reified C> workflow(name: String, block: WorkflowBuilder<C>.() -> Unit): Workflow<C>
fun <C> workflow(name: String, serializer: KSerializer<C>, block: WorkflowBuilder<C>.() -> Unit): Workflow<C>
```

`C` is the context: one `@Serializable` type threaded through every node, and the whole of what
survives a restart.

`name` is what the store records and what an engine looks a definition up by, so it has to be stable
across deploys — renaming a workflow orphans every instance of it still in flight.

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
    branch(CHARGE) { payments.charge(context.card, key = "$instanceId:$stepName") }
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

The metadata is not decoration. Delivery is at-least-once, so correct step code is idempotent, and
the usual way to make a remote call idempotent is a key that is stable across replays of the same
node of the same instance — which is `"$instanceId:$stepName"`.

In a compensation, `attempt` counts that compensation's own attempts.

## Running one

```kotlin
fun WorkflowEngine(store: WorkflowStore, block: WorkflowEngineBuilder.() -> Unit = {}): WorkflowEngine

class WorkflowEngineBuilder {
    var json: Json                                   // default: stx-common's lenientJson
    fun register(workflow: Workflow<*>)
}

suspend fun <C> WorkflowEngine.start(workflow: Workflow<C>, context: C, id: String = …): WorkflowInstance<C>
suspend fun WorkflowEngine.resume(id: String): WorkflowRecord
suspend fun <C> WorkflowEngine.resume(workflow: Workflow<C>, id: String): WorkflowInstance<C>
suspend fun WorkflowEngine.cancel(id: String): WorkflowRecord
suspend fun WorkflowEngine.record(id: String): WorkflowRecord?
suspend fun WorkflowEngine.runnable(now: Instant = …, limit: Int = 32): List<String>
```

`start` **returns when the workflow finishes**, compensates or fails — not when it is submitted. A
caller that wants to hand the work off starts it from a coroutine of its own, or lets a worker pick
it up.

A workflow must be `register`ed before `start` will run it: an instance the engine cannot look up
again is an instance that cannot be resumed after a restart.

`cancel` unwinds first and lands `Cancelled`. A cancelled checkout that leaves the stock reserved is
not cancelled.

Asking for an instance somebody else is advancing is **not an error**: the engine takes the
instance's lock, and when it cannot it hands back what the store has rather than queueing.

`resume` is for an operator, a test, or an application with its own scheduler. For automatic
recovery, use [the worker](#the-worker).

## Status

| Status | Means | Terminal |
| --- | --- | --- |
| `Running` | A node is running, or the next one is about to | |
| `Awaiting` | Stopped until a named signal arrives. **Not reachable in phase one** | |
| `Sleeping` | Stopped until a point in time. **Not reachable in phase one** | |
| `Compensating` | A node failed for good; the journal is being unwound | |
| `Completed` | Every node succeeded | yes |
| `Compensated` | A node failed, and every compensation owed for it succeeded | yes |
| `Failed` | A node failed, **and a compensation failed too** | yes |
| `Cancelled` | Stopped on request; whatever had run was compensated first | yes |

`Compensated` is the ordinary outcome of a workflow that failed. `Failed` is the one that asks for a
person, and it is the only status exempt from a store's retention.

`Awaiting` and `Sleeping` have no writer yet. They are declared, and the engine's loop and the store's
index are already shaped around them, so the phase that adds human approval adds a node type and a
`signal` call rather than a migration of every record already written.

## The persisted record

```kotlin
@Serializable
data class WorkflowRecord(
    val id: String,
    val workflow: String,
    val status: WorkflowStatus,
    val context: JsonElement,
    val journal: List<JournalEntry> = emptyList(),
    val awaiting: String? = null,       // phase two
    val wakeAt: Instant? = null,        // phase two
    val error: WorkflowError? = null,
    val cancelled: Boolean = false,
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
    val outcome: NodeOutcome,           // Succeeded | Failed | Compensated | CompensationFailed
    val attempts: Int,
    val value: JsonElement? = null,     // a leg's result, or the arm a branch took
    val error: WorkflowError? = null,
    val at: Instant,
)
```

**The journal is the only record of progress** — there is no cursor. Entries are appended, never
rewritten, so a node's **last** entry is its current state; everything that asks "has this run?" asks
it that way. `record.latest(node)` and `record.succeeded(node)` are how.

## Stores

```kotlin
interface WorkflowStore {
    suspend fun create(record: WorkflowRecord)
    suspend fun load(id: String): WorkflowRecord?
    suspend fun save(record: WorkflowRecord, expectedVersion: Long): Boolean
    suspend fun runnable(now: Instant, limit: Int): List<String>
    suspend fun <T> guarded(id: String, block: suspend () -> T): T?
}
```

Five methods, and that is the whole of what a new backing store has to answer for.

- **`save` must be conditional.** Returning true when the stored version was not `expectedVersion`
  turns a lost race into a lost journal.
- **`guarded` declines, it does not queue.** Null means somebody else holds it; the caller moves on.
  An implementation over a lock with a TTL has to renew it while the block runs.

Two ship:

| | |
| --- | --- |
| `InMemoryStore` | In the core module. The reference implementation of the contract — the conditional write really is conditional, `guarded` really excludes. What the engine's own specs run against |
| `RedisWorkflowStore` | In `stx-workflow-redis`. See [its README](../libs/stx-workflow/stx-workflow-redis/README.md) for the key layout, the lease and the retention |

```kotlin
class RedisWorkflowStore(
    redis: Redis,
    lease: Duration = 30.seconds,
    retention: Duration? = 7.days,
    json: Json = redis.json,
)
```

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

Opt-in by construction: nothing starts one, and putting `stx-workflow-redis` on a classpath starts no
background work.

It asks the store what is due and calls `resume` on each. There is no claim step — the engine takes
the instance's lock itself and returns quietly when somebody else has it, so two workers pulling the
same id is the normal shape of this rather than a race to prevent. It runs on a `CoroutineScope` the
caller owns and cancels; `close()` stops it and touches neither the engine nor the connection under
it.
