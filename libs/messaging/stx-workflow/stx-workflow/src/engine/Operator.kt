package com.softistx.workflow.engine

import com.softistx.workflow.ChildFailedException
import com.softistx.workflow.ChildLostException
import com.softistx.workflow.NonRetryableException
import com.softistx.workflow.WorkflowConflictException
import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowNotFoundException
import com.softistx.workflow.WorkflowNotRetryableException
import com.softistx.workflow.WorkflowNotUndoableException
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.store.WorkflowRecord
import kotlinx.serialization.SerializationException

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
 * The same holds for a child left [WorkflowStatus.Failed] by a compensation of its own: this throws
 * a [NonRetryableException] caused by a [ChildFailedException], so the parent fails on the node too
 * without retrying it. Retry the child with [retryCompensation], then the parent.
 *
 * Only `Cancelled` and `Compensated` count as taken back. A child another worker holds the lock of
 * is handed back unchanged (still running, waiting or completed): that throws a retryable
 * [ChildFailedException], so the parent never lands `Compensated` over effects that still stand.
 */
internal suspend fun WorkflowEngine.undoChild(id: String): WorkflowRecord {
    val record = store.load(id) ?: throw ChildLostException(id)
    val taken = if (record.status == WorkflowStatus.Completed) undo(id) else cancel(id)
    return when (taken.status) {
        WorkflowStatus.Cancelled, WorkflowStatus.Compensated -> taken
        // The child's own compensation failed: retrying the node would find it still `Failed`, so the
        // parent's retry policy must not spend its backoff on it. A person retries the child first.
        WorkflowStatus.Failed ->
            throw NonRetryableException(
                "child instance '$id' is Failed: its own compensation failed; retry it with retryCompensation, then the parent",
                ChildFailedException(id, taken.workflow, taken.status),
            )
        // `cancel` and `undo` hand back the stored record when another worker holds the child's lock,
        // so the child is still running, waiting or completed and its effects stand. Retryable: the
        // lock may be free by the next attempt.
        else -> throw ChildFailedException(id, taken.workflow, taken.status)
    }
}

/** What [WorkflowEngine.retryCompensation] does; its contract is documented there. */
internal suspend fun WorkflowEngine.retryFailedCompensation(id: String): WorkflowRecord {
    val record = store.load(id) ?: throw WorkflowNotFoundException(id)
    val workflow = definitionFor(record)
    val advanced =
        store.guarded(id) {
            val fresh = store.load(id) ?: throw WorkflowNotFoundException(id)
            val undecodable = fresh.failedOnUndecodableContext()
            if (fresh.status != WorkflowStatus.Failed || (fresh.failedCompensations().isEmpty() && !undecodable)) {
                throw WorkflowNotRetryableException(id, fresh.status)
            }
            val run =
                try {
                    Run(this, store, json, workflow, fresh)
                } catch (e: SerializationException) {
                    throw WorkflowNotRetryableException(
                        id,
                        fresh.status,
                        "workflow instance '$id' has a stored context that no longer decodes " +
                            "(${e.message}); repair the stored context before retrying its compensation",
                    )
                }
            // An instance retired for its context holds no failed compensation to reopen: it is put
            // back to unwinding from where its journal stands, which is what recovering it means.
            if (undecodable) {
                run.checkpoint { it.copy(status = WorkflowStatus.Compensating) }
            } else {
                run.reopenFailedCompensations()
            }
            run.advance()
            run.record
        } ?: throw WorkflowConflictException(id)
    if (advanced.status.isTerminal) advanced.parent?.let { wake(it) }
    return advanced
}

/** An instance [runFor] retired because its stored context did not decode, as opposed to a failed compensation. */
private fun WorkflowRecord.failedOnUndecodableContext(): Boolean =
    status == WorkflowStatus.Failed && error?.node == UNDECODABLE_CONTEXT_NODE

/** What [WorkflowEngine.undo] does; its contract is documented there. */
internal suspend fun WorkflowEngine.undoCompleted(id: String): WorkflowRecord {
    val record = store.load(id) ?: throw WorkflowNotFoundException(id)
    if (record.status != WorkflowStatus.Completed) throw WorkflowNotUndoableException(id, record.status)
    return advance(definitionFor(record), record, cancelling = true, reopen = true).record
}
