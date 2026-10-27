package com.softistx.workflow.engine

import com.softistx.workflow.WorkflowError
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.store.JournalEntry
import com.softistx.workflow.store.NodeOutcome
import com.softistx.workflow.store.WorkflowRecord
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
 * That person fixes what the compensation choked on and calls `WorkflowEngine.retryCompensation`,
 * which is [reopenFailedCompensations] followed by this function.
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

/** The nodes whose compensation failed and has not been run again since. */
internal fun WorkflowRecord.failedCompensations(): List<String> =
    journal.map { it.node }.distinct().filter { latest(it)?.outcome == NodeOutcome.CompensationFailed }

/**
 * Makes every failed compensation due again, and puts the instance back to [WorkflowStatus.Compensating].
 *
 * The journal is append-only, so "due again" is a new `Succeeded` entry for the node — which is what
 * its effect still is, since the undo never took it back — carrying the value of the entry it
 * replaces, because a fan-out leg's compensation reads it. The `CompensationFailed` entry stays
 * behind it as history. Compensations that already succeeded are untouched, which is what keeps the
 * unwind that follows from refunding a charge twice. One write carries the entries and the status,
 * so a crash cannot leave one without the other.
 */
internal suspend fun <C> Run<C>.reopenFailedCompensations() {
    val now = Clock.System.now()
    val due =
        record.failedCompensations().map { node ->
            val effect = record.journal.lastOrNull { it.node == node && it.outcome == NodeOutcome.Succeeded }
            JournalEntry(node, NodeOutcome.Succeeded, effect?.attempts ?: 1, effect?.value, error = null, at = now)
        }
    checkpoint { it.copy(status = WorkflowStatus.Compensating, journal = it.journal + due) }
}

private fun <C> Run<C>.nextUndo(): JournalEntry? =
    record.journal.asReversed().firstOrNull { entry -> workflow.undo.containsKey(entry.node) && owes(entry.node) }

/**
 * True when [node] still has something to take back.
 *
 * For an ordinary node that is "its last entry says it succeeded": one that failed did nothing that
 * needs undoing. A `child` node reads the same rule differently because what it owes is not an
 * effect but an **instance**, and that instance exists from the moment the node started it — whether
 * the node then succeeded, failed because the child did, or was still waiting when its deadline ran
 * out. Leaving that last one alone would be a workflow that quietly leaks a running instance every
 * time a child is slow.
 */
private fun <C> Run<C>.owes(node: String): Boolean =
    when (record.latest(node)?.outcome) {
        NodeOutcome.Succeeded -> true
        NodeOutcome.Paused, NodeOutcome.Failed -> node in workflow.children
        else -> false
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
    // A child node's undo is not a block this declaration holds — it is the child instance running
    // its own compensations, in its own reverse order, checkpointed in its own journal. Everything
    // else about compensating it is the same, retry policy and journal entry included.
    val child = workflow.children[entry.node]
    return try {
        val outcome =
            attempt(undo.retry, undo.timeout) { n ->
                if (child != null) {
                    engine.undoChild("${record.id}/${entry.node}")
                } else {
                    undo.block(scope(entry.node, n, startedAt), entry.value, json)
                }
            }
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
