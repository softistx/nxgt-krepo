package com.strange.workflow

import com.strange.common.serialization.lenientJson
import com.strange.workflow.dsl.Signal
import com.strange.workflow.engine.Run
import com.strange.workflow.engine.advance
import com.strange.workflow.store.WorkflowRecord
import com.strange.workflow.store.WorkflowStore
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
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
    private val store: WorkflowStore,
    private val json: Json,
    private val definitions: Map<String, Workflow<*>>,
    /** How often a parent parked on a `child` node looks again. See [com.strange.workflow.dsl.child]. */
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
    suspend fun undo(id: String): WorkflowRecord {
        val record = store.load(id) ?: throw WorkflowNotFoundException(id)
        if (record.status != WorkflowStatus.Completed) throw WorkflowNotUndoableException(id, record.status)
        return advance(definitionFor(record), record, cancelling = true, reopen = true).record
    }

    /**
     * Takes back a child instance, whichever side of the finish line it is on.
     *
     * A parent unwinds for reasons that have nothing to do with the child, so it reaches this node
     * with the child in any state: finished, and the answer is [undo]; still running or still
     * waiting — a deadline that ran out is the usual way — and the answer is [cancel]. Both end with
     * the child's own compensations having run, in its own journal, which is the only thing the
     * parent actually wants.
     *
     * A child that is no longer in the store cannot be taken back, and saying so is the point: this
     * fails the parent's compensation, the parent lands [WorkflowStatus.Failed], and a person gets
     * an instance to look at. Pretending it was undone would be the version nobody finds out about.
     */
    internal suspend fun undoChild(id: String): WorkflowRecord {
        val record = store.load(id) ?: throw ChildLostException(id)
        return if (record.status == WorkflowStatus.Completed) undo(id) else cancel(id)
    }

    suspend fun record(id: String): WorkflowRecord? = store.load(id)

    /**
     * The instances in [status], most recently updated first.
     *
     * `find(WorkflowStatus.Failed)` is the operator's inbox: every instance whose compensation could
     * not be made to work, which is the one outcome this engine deliberately refuses to resolve on
     * its own. Reading one and calling [resume] on it is what a person does after fixing whatever
     * the compensation was failing on.
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

    private fun definitionFor(record: WorkflowRecord): Workflow<Any?> {
        @Suppress("UNCHECKED_CAST")
        return definitions[record.workflow] as Workflow<Any?>?
            ?: throw WorkflowUnknownException(record.workflow)
    }

    /**
     * Takes the instance's lock and moves it along.
     *
     * A lock somebody else holds is **not an error**: two workers pulling the same id is the normal
     * shape of the claim protocol, and the one that loses hands back what the store has rather than
     * queueing behind work that is already being done.
     */
    private suspend fun <C> advance(
        workflow: Workflow<C>,
        record: WorkflowRecord,
        cancelling: Boolean = false,
        reopen: Boolean = false,
    ): WorkflowInstance<C> {
        if (record.status.isTerminal && !reopen) return instance(workflow, record)
        val advanced = advanceOnce(workflow, record, cancelling, reopen = reopen) ?: store.load(record.id) ?: record
        return instance(workflow, advanced)
    }

    /**
     * Tells a parent its child has finished, best effort.
     *
     * Best effort is the whole design, not a shortcut. The child's terminal state and the parent's
     * progress are two records and cannot be written together, so this is what makes the handoff
     * *prompt* while the parent's poll is what makes it *correct*. It therefore must not be able to
     * fail the child: the child did its job, and an exception thrown on the way to telling somebody
     * would undo work that succeeded.
     *
     * Failing to take the parent's lock is the ordinary case, not an error — it is what happens when
     * the parent is the very run that started this child, and that run reads the result itself
     * rather than waiting to be told.
     */
    private suspend fun wake(parent: String) {
        try {
            resume(parent)
        } catch (_: WorkflowException) {
            // The parent's poll will come back for it.
        }
    }

    /**
     * Starts a child instance, or picks up the one a previous attempt already started.
     *
     * The id is derived from the parent's, so "did I already start it" is answered by looking rather
     * than by a flag: a parent that died between the child's creation and its own checkpoint finds
     * the child here and carries on with it instead of starting a second.
     */
    internal suspend fun <D> startChild(
        workflow: Workflow<D>,
        context: D,
        id: String,
        parent: String,
    ): WorkflowRecord {
        require(definitions[workflow.name] === workflow) {
            "child workflow '${workflow.name}' is not registered with this engine — an instance it cannot " +
                "look up again is an instance that cannot be resumed after a restart"
        }
        store.load(id)?.let { return advance(workflow, it).record }
        val now = Clock.System.now()
        val record =
            WorkflowRecord(
                id = id,
                workflow = workflow.name,
                status = WorkflowStatus.Running,
                context = json.encodeToJsonElement(workflow.serializer, context),
                parent = parent,
                createdAt = now,
                updatedAt = now,
            )
        store.create(record)
        return advance(workflow, record).record
    }

    /**
     * One try at the lock: the record as it stands afterwards, or null when somebody else had it.
     *
     * The two callers want opposite things from that null, which is why it is returned rather than
     * decided here. [advance] hands back what the store has, because a worker losing a claim is the
     * protocol working. [signal] tries again, because it is holding a payload nobody else has.
     */
    private suspend fun <C> advanceOnce(
        workflow: Workflow<C>,
        record: WorkflowRecord,
        cancelling: Boolean = false,
        delivery: Delivery? = null,
        reopen: Boolean = false,
    ): WorkflowRecord? {
        val advanced = guardedAdvance(workflow, record, cancelling, delivery, reopen) ?: return null
        // Outside the lock and after the write, so the parent that runs next reads a child that is
        // durably finished and no longer held. Every way an instance can reach a terminal status
        // comes through here — a run, a signal, a cancel, an undo — which is the only reason one
        // line can be trusted to cover them all.
        if (advanced.status.isTerminal) advanced.parent?.let { wake(it) }
        return advanced
    }

    private suspend fun <C> guardedAdvance(
        workflow: Workflow<C>,
        record: WorkflowRecord,
        cancelling: Boolean,
        delivery: Delivery?,
        reopen: Boolean,
    ): WorkflowRecord? =
        store.guarded(record.id) {
            val fresh = store.load(record.id) ?: throw WorkflowNotFoundException(record.id)
            if (fresh.status.isTerminal && !reopen) {
                if (delivery != null) throw WorkflowNotAwaitingException(fresh.id, delivery.signal, fresh.status)
                return@guarded fresh
            }
            val run = runFor(workflow, fresh) ?: return@guarded store.load(record.id)!!
            delivery?.let { deliver(run, it) }
            if (cancelling) {
                run.checkpoint {
                    it.copy(
                        status = WorkflowStatus.Compensating,
                        cancelled = true,
                        awaiting = null,
                        signals = emptyMap(),
                        wakeAt = null,
                    )
                }
            }
            run.advance()
            run.record
        }

    /**
     * Builds the run, or retires the instance when its context no longer decodes.
     *
     * A workflow redeployed with a field added to its context and no default cannot read the
     * instances the previous deploy left in flight. Letting that exception out would put the
     * instance straight back in the runnable index for the next poll, and a worker would spend the
     * rest of its life failing on the same id. So the decode failure is the instance's failure: it
     * is recorded, it leaves the index, and it waits for somebody who can do something about it.
     */
    private suspend fun <C> runFor(
        workflow: Workflow<C>,
        record: WorkflowRecord,
    ): Run<C>? =
        try {
            Run(this, store, json, workflow, record)
        } catch (e: SerializationException) {
            store.save(
                record.copy(
                    status = WorkflowStatus.Failed,
                    error = WorkflowError.of("<context>", e, attempts = 1),
                    updatedAt = Clock.System.now(),
                ),
                record.version,
            )
            null
        }

    /**
     * Writes the payload into the record, under the lock the caller already holds.
     *
     * It wakes the instance only when the instance was parked on *this* signal. A delivery that
     * arrived early, or for a later wait, leaves the status alone: the run that follows walks the
     * declaration as it would have anyway, and either reaches the wait and reads the payload or
     * parks again where it was. Waking an instance parked on a different signal would step over
     * that wait, which is the one thing an early delivery must not be allowed to do.
     */
    private suspend fun <C> deliver(
        run: Run<C>,
        delivery: Delivery,
    ) {
        val awaited = run.record.status == WorkflowStatus.Awaiting && run.record.awaiting == delivery.signal
        run.checkpoint {
            it.copy(
                status = if (awaited) WorkflowStatus.Running else it.status,
                awaiting = if (awaited) null else it.awaiting,
                wakeAt = if (awaited) null else it.wakeAt,
                signals = it.signals + (delivery.signal to delivery.payload),
            )
        }
    }

    private fun <C> instance(
        workflow: Workflow<C>,
        record: WorkflowRecord,
    ): WorkflowInstance<C> = WorkflowInstance(record, json.decodeFromJsonElement(workflow.serializer, record.context))
}

/**
 * How many times [WorkflowEngine.signal] tries for a lock somebody else is holding, and how long it
 * waits between tries.
 *
 * The lock is held while an instance is being advanced, so the wait this covers is one step, not one
 * approval. Four hundred milliseconds is long enough for the step that triggered the callback to
 * finish and short enough to sit inside the request the callback arrived on.
 */
private const val DELIVERY_ATTEMPTS = 5
private val DELIVERY_RETRY = 100.milliseconds

/** A signal on its way in: the name it belongs to, and its payload already encoded. */
private class Delivery(
    val signal: String,
    val payload: JsonElement,
)

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

class WorkflowEngineBuilder internal constructor() {
    internal val definitions = mutableMapOf<String, Workflow<*>>()

    /**
     * What a context is written and read with.
     *
     * The default is lenient about unknown keys, because a stored context is a copy and not the
     * record: one written by the previous deploy, carrying a field this version has dropped, must
     * still read, or a rolling deploy becomes a fleet half of which cannot resume the other half's
     * instances. Pass a strict `Json` when that skew should be loud instead.
     */
    var json: Json = lenientJson

    /**
     * How often a parent parked on a `child` node looks at the child again.
     *
     * It is a safety net, not the mechanism: a child resumes its parent the moment it finishes, so
     * this only covers the process that died between those two writes. Shortening it makes recovery
     * from that faster and costs one load per parked parent per interval; lengthening it costs
     * nothing until something goes wrong.
     *
     * It also needs somebody polling — a `WorkflowWorker`, or an application scheduler calling
     * `resume`. An engine with no poller relies entirely on the child's own wake-up.
     */
    var childPoll: Duration = 1.minutes

    fun register(workflow: Workflow<*>) {
        require(definitions.put(workflow.name, workflow) == null) { "two workflows are registered as '${workflow.name}'" }
    }
}
