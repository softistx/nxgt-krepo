package com.strange.workflow.engine

import com.strange.workflow.WorkflowError
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.store.JournalEntry
import com.strange.workflow.store.NodeOutcome
import kotlin.time.Clock

/**
 * Undoes what ran, newest first.
 *
 * Only nodes whose **last** journal entry says they succeeded are undone: a node that failed has
 * nothing to take back, a node that was never reached has nothing either, and a node already
 * compensated must not be compensated twice. Each compensation is checkpointed as it finishes, so an
 * instance that dies halfway through unwinding picks up where it left off rather than refunding the
 * same charge again.
 *
 * **A compensation that fails stops the unwind.** The instance lands [WorkflowStatus.Failed] with
 * the node named, and nothing here tries to keep going. Undoing the steps *before* one whose undo
 * failed leaves the world in a shape nobody can describe — a released reservation for an order that
 * is still charged — and no policy this library could invent is better than a person looking at it.
 */
internal suspend fun <C> Run<C>.unwind() {
    while (true) {
        if (record.journal.any { record.latest(it.node)?.outcome == NodeOutcome.CompensationFailed }) {
            checkpoint { it.copy(status = WorkflowStatus.Failed) }
            return
        }
        val entry = nextUndo() ?: break
        if (!compensateNode(entry)) {
            checkpoint { it.copy(status = WorkflowStatus.Failed) }
            return
        }
    }
    checkpoint { it.copy(status = if (record.cancelled) WorkflowStatus.Cancelled else WorkflowStatus.Compensated) }
}

private fun <C> Run<C>.nextUndo(): JournalEntry? =
    record.journal.asReversed().firstOrNull { entry ->
        workflow.undo.containsKey(entry.node) && record.succeeded(entry.node) != null
    }

/**
 * Runs one node's compensation. False means it failed for good.
 *
 * The compensation is retried under **its node's own policy**: saying `retry { times = 3 }` on a
 * charge says the same about the refund, which is the reading almost everybody wants and the one
 * that needs no second knob to express.
 */
internal suspend fun <C> Run<C>.compensateNode(entry: JournalEntry): Boolean {
    val undo = workflow.undo[entry.node] ?: return true
    val startedAt = Clock.System.now()
    return try {
        val outcome = attempt(undo.retry, undo.timeout) { n -> undo.block(scope(entry.node, n, startedAt), entry.value, json) }
        journal(entry.node, NodeOutcome.Compensated, outcome.attempts)
        true
    } catch (failure: NodeFailure) {
        journal(
            node = entry.node,
            outcome = NodeOutcome.CompensationFailed,
            attempts = failure.attempts,
            error = WorkflowError.of(entry.node, failure.cause, failure.attempts),
        )
        false
    }
}
