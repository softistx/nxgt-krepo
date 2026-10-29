# Running a stx-workflow

What happens to a [declaration](workflow.md) once it runs: starting an instance, its statuses, the
persisted record, changing a declaration with instances in flight, the stores, the Ktor and Spring
Boot wiring and the worker. What a declaration may say is in [workflow.md](workflow.md).

- [Running one](#running-one)
- [Status](#status)
- [The persisted record](#the-persisted-record)
- [Changing a declaration that has instances in flight](#changing-a-declaration-that-has-instances-in-flight)
- [Stores](#stores)
- [In a Ktor application](#in-a-ktor-application)
- [In a Spring Boot application](#in-a-spring-boot-application)
- [The worker](#the-worker)

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
signal](workflow.md#waiting-for-a-signal). Two deliveries are refused, both loudly:

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
waiting for a person by design, through [`retryCompensation`](#the-operators-inbox). It is also what a [`child` node's](workflow.md#child-workflows) compensation
runs.

Asking for an instance somebody else is advancing is **not an error**: the engine takes the
instance's lock, and when it cannot it hands back what the store has rather than queueing.

`resume` is for an operator, a test, or an application with its own scheduler. For automatic
recovery, use [the worker](#the-worker). It does nothing to a terminal instance, `Failed` included:
that one is moved by `retryCompensation`.

## Status

| Status | Means | Terminal |
| --- | --- | --- |
| `Running` | A node is running, or the next one is about to | |
| `Awaiting` | Stopped until a named signal arrives, or until a [child workflow](workflow.md#child-workflows) finishes — `awaiting` says which | |
| `Sleeping` | Stopped until a point in time — a [`sleep`](workflow.md#waiting-for-a-clock), or a [start booked for later](#booking-a-start-for-later) that has not come round | |
| `Compensating` | A node failed for good; the journal is being unwound | |
| `Completed` | Every node succeeded | yes |
| `Compensated` | A node failed, and every compensation owed for it succeeded | yes |
| `Failed` | A node failed, **and a compensation failed too**; `retryCompensation` reopens it | yes |
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

// after fixing whatever the compensation choked on:
val record = engine.retryCompensation(stuck.first().id)
check(record.status == WorkflowStatus.Compensated || record.status == WorkflowStatus.Cancelled)
```

`Failed` is the one outcome the engine deliberately refuses to resolve on its own — a compensation
that could not be made to work — and `find` is what makes the status mean something. Without it the
only read is `record(id)`, and an operator does not have the id.

`retryCompensation(id)` is what resolves it afterwards. `resume` cannot: `Failed` is terminal, and a
resume hands a terminal instance back untouched. `retryCompensation` makes the failed compensation
due again and reopens the instance, so the unwind **continues from where it stopped**:

- compensations that already succeeded are not run a second time;
- the ones that never ran, because the unwind stopped at the failure, run after it, newest first;
- the instance lands `Compensated`, or `Cancelled` when the unwind was a `cancel` or an `undo`;
- a compensation that still fails leaves it `Failed`, and the call can be repeated as often as it
  takes. `error` keeps naming the node whose forward step failed.

The call refuses with `WorkflowNotRetryableException` when there is nothing to retry: an instance
that is not `Failed`, or a `Failed` one whose journal names no failed compensation. One `Failed`
instance without a failed compensation is recoverable: the one the engine retired because its stored
context no longer decodes (a redeploy changed the context type; `error` names the node `<context>`).
Once the context decodes again, `retryCompensation` runs its unwind from where its journal stands
and the instance lands `Compensated` (or `Cancelled`); while it still does not decode the call
refuses with a message asking for the stored context to be repaired, and nothing is reopened or
written. An unknown id is `WorkflowNotFoundException`, and an instance another process is
advancing is `WorkflowConflictException`, so an operator never mistakes a skipped retry for a done
one. For a parent whose [`child` node](workflow.md#child-workflows) could not be compensated, retry the **child**
first. A child still `Failed` fails the parent's compensation of that node too (`ChildFailedException`),
so the parent stays `Failed` until the child is fixed. A child another worker still held when the
parent unwound fails it the same way, with its running, waiting or completed `status`: wait for the
child to be free, then retry the parent.

Every store keeps an index for this: a sorted set per status in Redis, a `(status, updated_at)` index
in SQL, a compound index in MongoDB. It is the second thing all three maintain on every write.

**There is no ready-made HTTP endpoint for it, in either integration.** An admin route that lists
instances and restarts them is exactly the route that must not be open, and who may call it is a
question about your application rather than about this library. The few lines above are the route;
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
    unknownBackoff: Duration = 1.minutes,
    onError: (id: String, cause: Throwable) -> Unit = /* logs a warning */,
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

**It is bounded.** The permit is taken before a coroutine is launched, so a backlog larger than
`concurrency` waits in the loop and never becomes one coroutine per id. An id already in flight in
this worker is skipped, and when a pass starts nothing the loop waits a `poll` instead of asking the
store again at once. A `resume` that throws is passed to `onError` (the default logs a warning) and
its id is left alone for a `poll`. The default logs because a worker must not fail silently. Every
id is parked for a `poll` after its `resume` returns, so a lock held by another engine cannot make
the loop spin, and the worker asks the store for `batch` plus the parked and in-flight ids so
unregistered instances cannot starve the real ones. An instance of a workflow this engine has not
registered is not
failed, since another deployment may own it: it is reported once per `unknownBackoff` (a minute by
default) and left alone for that long.
