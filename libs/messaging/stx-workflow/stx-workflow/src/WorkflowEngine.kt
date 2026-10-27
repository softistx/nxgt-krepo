package com.softistx.workflow

import com.softistx.workflow.dsl.Signal
import com.softistx.workflow.engine.DELIVERY_ATTEMPTS
import com.softistx.workflow.engine.DELIVERY_RETRY
import com.softistx.workflow.engine.Delivery
import com.softistx.workflow.engine.advance
import com.softistx.workflow.engine.advanceOnce
import com.softistx.workflow.engine.retryFailedCompensation
import com.softistx.workflow.engine.undoCompleted
import com.softistx.workflow.store.WorkflowRecord
import com.softistx.workflow.store.WorkflowStore
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Runs instances of the workflows it was given, against the store it was given.
 *
 * It owns neither. Whoever opened the `Redis` behind a `RedisWorkflowStore` closes it, which is why
 * there is nothing to close here.
 *
 * ```kotlin
 * val engine = WorkflowEngine(RedisWorkflowStore(redis)) { register(checkout) }
 * val instance = engine.start(checkout, Checkout(items, card))
 * ```
 *
 * **[resume] is for an operator, a test, or an application with a scheduler of its own.** Anybody
 * who wants instances picked up automatically after a crash wants a `WorkflowWorker`; calling
 * [resume] in a loop is that class, written again and worse.
 */
class WorkflowEngine internal constructor(
    internal val store: WorkflowStore,
    internal val json: Json,
    internal val definitions: Map<String, Workflow<*>>,
    /** How often a parent parked on a `child` node looks again. See [com.softistx.workflow.dsl.child]. */
    internal val childPoll: Duration,
) {
    /**
     * Creates an instance and runs it as far as it goes.
     *
     * It returns when the workflow finishes, compensates, or fails — not when it is submitted. A
     * caller that wants to hand the work off rather than wait for it starts the instance from a
     * coroutine of its own, or lets a worker pick it up.
     */
    suspend fun <C> start(
        workflow: Workflow<C>,
        context: C,
        id: String = UUID.randomUUID().toString(),
    ): WorkflowInstance<C> {
        require(definitions[workflow.name] === workflow) {
            "workflow '${workflow.name}' is not registered with this engine — an instance it cannot look up again " +
                "is an instance that cannot be resumed after a restart"
        }
        val now = Clock.System.now()
        val record =
            WorkflowRecord(
                id = id,
                workflow = workflow.name,
                status = WorkflowStatus.Running,
                context = json.encodeToJsonElement(workflow.serializer, context),
                createdAt = now,
                updatedAt = now,
            )
        store.create(record)
        return advance(workflow, record)
    }

    /** Picks an instance up where it stopped. Its definition is looked up by the name in the record. */
    suspend fun resume(id: String): WorkflowRecord {
        val record = store.load(id) ?: throw WorkflowNotFoundException(id)
        return advance(definitionFor(record), record).record
    }

    /** [resume], for a caller that knows the workflow and wants its context back typed. */
    suspend fun <C> resume(
        workflow: Workflow<C>,
        id: String,
    ): WorkflowInstance<C> {
        val record = store.load(id) ?: throw WorkflowNotFoundException(id)
        require(record.workflow == workflow.name) { "instance '$id' is a '${record.workflow}', not a '${workflow.name}'" }
        return advance(workflow, record)
    }

    /**
     * Delivers a signal to an instance, and runs it on from there.
     *
     * ```kotlin
     * engine.signal(id, APPROVAL, Approval(by = "ops", note = "verified by phone"))
     * ```
     *
     * This is the whole of the human-approval story from the outside: an HTTP handler, a message
     * consumer or an operator's console calls this, and the workflow continues in *that* process,
     * from where it stopped, with the payload folded into its context by the `await` block.
     *
     * **The instance does not have to be waiting yet.** A payment provider that calls back before
     * the step which asked it to has even returned is not a caller doing something wrong, and a
     * delivery refused for arriving early would leave correctness resting on whether that provider
     * retries. So a delivery is written to the record as soon as it is accepted, under the name of
     * the signal it belongs to, and the `await` reads it whenever it gets there.
     *
     * Two deliveries are still refused, both loudly. One naming a signal the definition has no
     * `await` for — [WorkflowUnknownSignalException] — because storing it would tell the caller it
     * landed when nothing will ever read it. And one to an instance that has already finished —
     * [WorkflowNotAwaitingException] — which is the approval clicked twice.
     *
     * Delivery and the run that follows happen under the instance's lock, in one conditional write,
     * so two people approving at the same instant cannot both wake it. When another process holds
     * that lock this **waits and tries again** rather than returning what the store has: a
     * delivery quietly dropped because the instance happened to be mid-step is the exact failure
     * this method exists to rule out. A lock still held after that throws
     * [WorkflowConflictException], and the payload was not written — an HTTP handler should answer
     * with something the caller will retry.
     */
    suspend fun <T> signal(
        id: String,
        signal: Signal<T>,
        payload: T,
    ): WorkflowRecord {
        var record = store.load(id) ?: throw WorkflowNotFoundException(id)
        val workflow = definitionFor(record)
        if (signal.name !in workflow.signals) throw WorkflowUnknownSignalException(workflow.name, signal.name)
        val delivery = Delivery(signal.name, json.encodeToJsonElement(signal.serializer, payload))
        repeat(DELIVERY_ATTEMPTS) { round ->
            if (round > 0) {
                delay(DELIVERY_RETRY)
                record = store.load(id) ?: throw WorkflowNotFoundException(id)
            }
            if (record.status.isTerminal) throw WorkflowNotAwaitingException(id, signal.name, record.status)
            advanceOnce(workflow, record, delivery = delivery)?.let { return it }
        }
        throw WorkflowConflictException(id)
    }

    /**
     * Stops an instance, undoing whatever it had already done.
     *
     * It unwinds first and lands [WorkflowStatus.Cancelled], rather than abandoning the effects
     * where they are — a cancelled checkout that leaves the stock reserved is not cancelled.
     */
    suspend fun cancel(id: String): WorkflowRecord {
        val record = store.load(id) ?: throw WorkflowNotFoundException(id)
        if (record.status.isTerminal) return record
        return advance(definitionFor(record), record, cancelling = true).record
    }

    /**
     * Books an instance to begin at [at], and returns without running any of it.
     *
     * ```kotlin
     * engine.startAt(reminder, Reminder(bookingId), at = booking.startsAt - 24.hours)
     * ```
     *
     * The instance exists from this call: it has an id, a context and a record, and `cancel` works
     * on it like any other. What it does not have is a journal, which is exactly what tells the
     * engine it has not begun — see [WorkflowRecord.isScheduled].
     *
     * **Something has to come back for it.** It sits in the store's due-time index scored at [at],
     * where a `WorkflowWorker` or an application scheduler calling [resume] will find it. An engine
     * with nobody polling has booked an instance that never starts, which is the same rule `sleep`
     * already lives under and for the same reason: nothing here holds a timer.
     *
     * An [at] that has already passed is not an error and is not deferred — it starts now. A booking
     * for a moment in the past is a booking that is due.
     *
     * There is no recurrence here, and it is not an omission. "Every night at three" is a schedule,
     * and a schedule is not an instance: it outlives every run of it, it has to survive being paused
     * and edited, and it needs a store and a vocabulary of its own. Building it out of one instance
     * that re-books another would give a chain in which one lost link ends the series silently.
     */
    suspend fun <C> startAt(
        workflow: Workflow<C>,
        context: C,
        at: Instant,
        id: String = UUID.randomUUID().toString(),
    ): WorkflowInstance<C> {
        val now = Clock.System.now()
        if (at <= now) return start(workflow, context, id)
        require(definitions[workflow.name] === workflow) {
            "workflow '${workflow.name}' is not registered with this engine — an instance it cannot look up again " +
                "is an instance that cannot be resumed after a restart"
        }
        val record =
            WorkflowRecord(
                id = id,
                workflow = workflow.name,
                status = WorkflowStatus.Sleeping,
                context = json.encodeToJsonElement(workflow.serializer, context),
                wakeAt = at,
                createdAt = now,
                updatedAt = now,
            )
        store.create(record)
        return instance(workflow, record)
    }

    /** [startAt], counted from now. */
    suspend fun <C> startAfter(
        workflow: Workflow<C>,
        context: C,
        delay: Duration,
        id: String = UUID.randomUUID().toString(),
    ): WorkflowInstance<C> = startAt(workflow, context, Clock.System.now() + delay, id)

    /**
     * Reverses an instance that already **succeeded**, landing it [WorkflowStatus.Cancelled].
     *
     * ```kotlin
     * engine.undo(orderId)     // the order shipped, and the customer sent it back
     * ```
     *
     * This is [cancel]'s counterpart across the finish line, and they are kept apart on purpose.
     * `cancel` stops something in flight and is a safe no-op on anything terminal — every caller
     * that reaches for it defensively depends on that, and folding the two together would turn one
     * of those calls into a refund nobody asked for. `undo` is the one you have to mean.
     *
     * Nothing else is undoable, and each refusal has a reason: an instance still running is
     * [cancel]'s, one already `Compensated` or `Cancelled` has been unwound and undoing it twice
     * would take back a compensation, and one that is `Failed` is waiting for a person by design.
     *
     * It is also what a `child` node's compensation runs. Undoing a parent means undoing the child
     * it delegated to — its own compensations, in its own reverse order, checkpointed in its own
     * journal — which is why this is a verb rather than something the engine only does internally.
     */
    suspend fun undo(id: String): WorkflowRecord = undoCompleted(id)

    /**
     * Runs the compensation that failed again, and carries the unwind on from there.
     *
     * ```kotlin
     * engine.find(WorkflowStatus.Failed).forEach { engine.retryCompensation(it.id) }
     * ```
     *
     * This is what an operator calls after fixing whatever the compensation choked on. [resume]
     * cannot do it: a `Failed` instance is terminal, and a resume hands it back untouched. Here the
     * failed compensation is made due again and the instance reopens, so the unwind **continues
     * from where it stopped** — the compensations that already succeeded are not run a second time,
     * and the ones that never ran because the unwind stopped are run after it, newest first. The
     * result is [WorkflowStatus.Compensated], or [WorkflowStatus.Cancelled] when the unwind was a
     * cancel or an [undo], or `Failed` again if the compensation still fails — it can be retried as
     * often as it takes.
     *
     * For a parent with a `child` node whose compensation failed, retry the child first, then the
     * parent: a child still `Failed` fails the parent's compensation of that node too.
     *
     * Two refusals, both [WorkflowException]s. An id with no instance is [WorkflowNotFoundException].
     * Any instance that is not `Failed` with a failed compensation in its journal is
     * [WorkflowNotRetryableException]: there is nothing to run again. So is one whose stored context
     * no longer decodes (a redeploy changed the context type): the compensations need the context,
     * so it is refused before anything is reopened, nothing is written and `error` keeps naming the
     * node whose forward step failed. The stored context has to be repaired first.
     *
     * When another process holds the instance this throws [WorkflowConflictException] rather than
     * returning quietly: an operator who asked for a retry has to know it did not happen.
     */
    suspend fun retryCompensation(id: String): WorkflowRecord = retryFailedCompensation(id)

    suspend fun record(id: String): WorkflowRecord? = store.load(id)

    /**
     * The instances in [status], most recently updated first.
     *
     * `find(WorkflowStatus.Failed)` is the operator's inbox: every instance whose compensation could
     * not be made to work, which is the one outcome this engine deliberately refuses to resolve on
     * its own. Reading one and calling [retryCompensation] on it is what a person does after fixing
     * whatever the compensation was failing on.
     */
    suspend fun find(
        status: WorkflowStatus,
        limit: Int = 50,
        offset: Int = 0,
    ): List<WorkflowRecord> = store.find(status, limit, offset)

    /** The instances the store says are due to be advanced. What a worker polls. */
    suspend fun runnable(
        now: Instant = Clock.System.now(),
        limit: Int = 32,
    ): List<String> = store.runnable(now, limit)

    internal fun definitionFor(record: WorkflowRecord): Workflow<Any?> {
        @Suppress("UNCHECKED_CAST")
        return definitions[record.workflow] as Workflow<Any?>?
            ?: throw WorkflowUnknownException(record.workflow)
    }
    internal fun <C> instance(
        workflow: Workflow<C>,
        record: WorkflowRecord,
    ): WorkflowInstance<C> = WorkflowInstance(record, json.decodeFromJsonElement(workflow.serializer, record.context))
}

/**
 * Builds an engine.
 *
 * ```kotlin
 * val engine = WorkflowEngine(RedisWorkflowStore(redis)) {
 *     register(checkout)
 *     register(refund)
 * }
 * ```
 */
fun WorkflowEngine(
    store: WorkflowStore,
    block: WorkflowEngineBuilder.() -> Unit = {},
): WorkflowEngine {
    val builder = WorkflowEngineBuilder().apply(block)
    return WorkflowEngine(store, builder.json, builder.definitions.toMap(), builder.childPoll)
}
