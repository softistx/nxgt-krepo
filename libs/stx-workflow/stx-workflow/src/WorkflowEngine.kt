package com.strange.workflow

import com.strange.common.serialization.lenientJson
import com.strange.workflow.engine.Run
import com.strange.workflow.engine.advance
import com.strange.workflow.store.WorkflowRecord
import com.strange.workflow.store.WorkflowStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Clock
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

        val advanced =
            store.guarded(record.id) {
                val fresh = store.load(record.id) ?: throw WorkflowNotFoundException(record.id)
                if (fresh.status.isTerminal) return@guarded fresh
                val run = runFor(workflow, fresh) ?: return@guarded store.load(record.id)!!
                if (cancelling) run.checkpoint { it.copy(status = WorkflowStatus.Compensating, cancelled = true) }
                run.advance()
                run.record
            } ?: store.load(record.id) ?: record

        return instance(workflow, advanced)
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

    private fun <C> instance(
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
