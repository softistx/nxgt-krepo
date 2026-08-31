package com.strange.workflow.store

import com.strange.workflow.WorkflowError
import com.strange.workflow.WorkflowStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/**
 * One instance, as the store holds it.
 *
 * The **context is stored encoded**. A store does not know the workflow's context type, has no
 * `KSerializer` for it and no reason to want one; keeping it as a [JsonElement] is what lets
 * [WorkflowStore] be a non-generic interface with five methods instead of a generic one every
 * implementation has to thread a type parameter through. The engine encodes on the way in and
 * decodes on the way out, where the `KSerializer<C>` actually is.
 *
 * [awaiting] and [wakeAt] have no writer in phase one. They are here for the same reason
 * [WorkflowStatus.Awaiting] is: a record already written to Redis should not need rewriting when a
 * human-approval step arrives.
 */
@Serializable
data class WorkflowRecord(
    val id: String,
    /** The workflow's name, which is how [com.strange.workflow.WorkflowEngine] finds the definition again. */
    val workflow: String,
    val status: WorkflowStatus,
    val context: JsonElement,
    val journal: List<JournalEntry> = emptyList(),
    /** The signal this instance is stopped on. Phase two. */
    val awaiting: String? = null,
    /** When this instance should be picked up again. Phase two. */
    val wakeAt: Instant? = null,
    val error: WorkflowError? = null,
    /**
     * True when the unwind was asked for rather than caused by a failure.
     *
     * It is persisted rather than held in memory because a cancel that is itself interrupted has to
     * come back as a cancel: without this, resuming it would finish the unwind and call the result
     * `Compensated`, which says the workflow failed when in fact somebody stopped it.
     */
    val cancelled: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    /**
     * Bumped by every write, and checked by it.
     *
     * The instance lock is what normally keeps two engines apart, but a lock on one Redis is not a
     * consensus — a failover to a replica that had not yet seen the `SET` hands it to two holders.
     * This is the second line: the loser's write is refused rather than silently overwriting a
     * journal it never saw.
     *
     * It is **not serialized**. A store keeps it beside the record rather than inside it, so a
     * conditional write can compare it without decoding the whole document first — and so there is
     * only ever one copy of it to keep true.
     */
    @Transient
    val version: Long = 0,
) {
    /**
     * The last thing that happened to [node], which is its current state.
     *
     * Entries are appended, never rewritten, so a node that ran and was later undone has two of
     * them and only the second one is true now. Everything that asks "has this run?" has to ask it
     * this way, or an unwind would keep finding the `Succeeded` entry it just compensated.
     */
    fun latest(node: String): JournalEntry? = journal.lastOrNull { it.node == node }

    /** The entry for [node] if it has run and has not since been undone, else null. */
    fun succeeded(node: String): JournalEntry? = latest(node)?.takeIf { it.outcome == NodeOutcome.Succeeded }
}
