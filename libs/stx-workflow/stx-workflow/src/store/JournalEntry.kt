package com.softistx.workflow.store

import com.softistx.workflow.WorkflowError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/** What became of one node. */
@Serializable
enum class NodeOutcome {
    Succeeded,
    Failed,

    /**
     * Reached, and stopped there — on a signal or on a clock.
     *
     * It is what tells a resume "this instance is parked *here*" without a second field beside the
     * journal to disagree with it. The entry is superseded by a `Succeeded` one when the wait ends,
     * because [com.softistx.workflow.store.WorkflowRecord.latest] reads the last entry for a node and
     * not the first.
     */
    Paused,

    /** Succeeded, and has since been undone. */
    Compensated,

    /** Succeeded, and undoing it failed. This is what leaves an instance `Failed`. */
    CompensationFailed,
}

/**
 * One line of what happened, appended as it happens.
 *
 * **The journal is the only record of progress.** There is no cursor beside it: a cursor would be a
 * second answer to the same question, and the two disagree the moment a node is nested — inside a
 * branch arm, or as one leg of a fan-out — because an index into the top-level list cannot say
 * which of a branch's own steps have run. Resuming therefore means walking the workflow's nodes and
 * skipping the ones that already have a [NodeOutcome.Succeeded] entry here, at every level.
 *
 * That is also why [node] is a **qualified** name — `"provision/charge"` for a branch of the
 * `provision` fan-out — and why node names have to be unique, which [com.softistx.workflow.workflow]
 * checks when the workflow is built rather than when it first runs.
 */
@Serializable
data class JournalEntry(
    val node: String,
    val outcome: NodeOutcome,
    val attempts: Int,
    /**
     * What the node produced, when that is not the context: the output of one leg of a fan-out, or
     * the name of the arm a branch took. A plain step writes its result into the context, so it
     * leaves this null.
     */
    val value: JsonElement? = null,
    val error: WorkflowError? = null,
    val at: Instant,
)
