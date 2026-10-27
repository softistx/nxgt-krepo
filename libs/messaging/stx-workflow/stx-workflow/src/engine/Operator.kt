package com.softistx.workflow.engine

import com.softistx.workflow.ChildFailedException
import com.softistx.workflow.ChildLostException
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
 * [ChildFailedException], so the parent fails on the node too. Retry the child with
 * [retryCompensation], then the parent.
 */
internal suspend fun WorkflowEngine.undoChild(id: String): WorkflowRecord {
    val record = store.load(id) ?: throw ChildLostException(id)
    val taken = if (record.status == WorkflowStatus.Completed) undo(id) else cancel(id)
    // `cancel` hands a terminal instance back untouched, and `undo` lands `Failed` when the child's
    // own compensation fails: either way the child is still `Failed`, its effects still standing,
    // and reporting it as taken back would hide that from the parent's unwind.
    if (taken.status == WorkflowStatus.Failed) throw ChildFailedException(id, taken.workflow, taken.status)
    return taken
}

/** What [WorkflowEngine.retryCompensation] does; its contract is documented there. */
internal suspend fun WorkflowEngine.retryFailedCompensation(id: String): WorkflowRecord {
    val record = store.load(id) ?: throw WorkflowNotFoundException(id)
    val workflow = definitionFor(record)
    val advanced =
        store.guarded(id) {
            val fresh = store.load(id) ?: throw WorkflowNotFoundException(id)
            if (fresh.status != WorkflowStatus.Failed || fresh.failedCompensations().isEmpty()) {
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
            run.reopenFailedCompensations()
            run.advance()
            run.record
        } ?: throw WorkflowConflictException(id)
    if (advanced.status.isTerminal) advanced.parent?.let { wake(it) }
    return advanced
}

/** What [WorkflowEngine.undo] does; its contract is documented there. */
internal suspend fun WorkflowEngine.undoCompleted(id: String): WorkflowRecord {
    val record = store.load(id) ?: throw WorkflowNotFoundException(id)
    if (record.status != WorkflowStatus.Completed) throw WorkflowNotUndoableException(id, record.status)
    return advance(definitionFor(record), record, cancelling = true, reopen = true).record
}
