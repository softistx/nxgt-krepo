package com.softistx.workflow.engine

import com.softistx.workflow.Workflow
import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowError
import com.softistx.workflow.WorkflowException
import com.softistx.workflow.WorkflowInstance
import com.softistx.workflow.WorkflowNotAwaitingException
import com.softistx.workflow.WorkflowNotFoundException
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.store.WorkflowRecord
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Takes the instance's lock and moves it along.
 *
 * A lock somebody else holds is **not an error**: two workers pulling the same id is the normal
 * shape of the claim protocol, and the one that loses hands back what the store has rather than
 * queueing behind work that is already being done.
 */
internal suspend fun <C> WorkflowEngine.advance(
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
internal suspend fun WorkflowEngine.wake(parent: String) {
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
internal suspend fun <D> WorkflowEngine.startChild(
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
internal suspend fun <C> WorkflowEngine.advanceOnce(
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

private suspend fun <C> WorkflowEngine.guardedAdvance(
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
private suspend fun <C> WorkflowEngine.runFor(
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
private suspend fun <C> WorkflowEngine.deliver(
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

/**
 * How many times [WorkflowEngine.signal] tries for a lock somebody else is holding, and how long it
 * waits between tries.
 *
 * The lock is held while an instance is being advanced, so the wait this covers is one step, not one
 * approval. Four hundred milliseconds is long enough for the step that triggered the callback to
 * finish and short enough to sit inside the request the callback arrived on.
 */
internal const val DELIVERY_ATTEMPTS = 5
internal val DELIVERY_RETRY = 100.milliseconds

/** A signal on its way in: the name it belongs to, and its payload already encoded. */
internal class Delivery(
    val signal: String,
    val payload: JsonElement,
)
