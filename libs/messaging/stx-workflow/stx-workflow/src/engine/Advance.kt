package com.softistx.workflow.engine

import com.softistx.workflow.Workflow
import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowError
import com.softistx.workflow.WorkflowInstance
import com.softistx.workflow.WorkflowNotAwaitingException
import com.softistx.workflow.WorkflowNotFoundException
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.store.WorkflowRecord
import kotlinx.serialization.SerializationException
import kotlin.time.Clock

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

/** The node name [runFor] records as the failure of an instance whose context no longer decodes. */
internal const val UNDECODABLE_CONTEXT_NODE = "<context>"

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
                error = WorkflowError.of(UNDECODABLE_CONTEXT_NODE, e, attempts = 1),
                updatedAt = Clock.System.now(),
            ),
            record.version,
        )
        null
    }
