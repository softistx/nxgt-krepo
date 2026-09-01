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
import kotlin.time.Duration.Companion.milliseconds
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
    ): WorkflowInstance<C> {
        if (record.status.isTerminal) return instance(workflow, record)
        val advanced = advanceOnce(workflow, record, cancelling) ?: store.load(record.id) ?: record
        return instance(workflow, advanced)
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
    ): WorkflowRecord? =
        store.guarded(record.id) {
            val fresh = store.load(record.id) ?: throw WorkflowNotFoundException(record.id)
            if (fresh.status.isTerminal) {
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
            Run(store, json, workflow, record)
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
    return WorkflowEngine(store, builder.json, builder.definitions.toMap())
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

    fun register(workflow: Workflow<*>) {
        require(definitions.put(workflow.name, workflow) == null) { "two workflows are registered as '${workflow.name}'" }
    }
}
