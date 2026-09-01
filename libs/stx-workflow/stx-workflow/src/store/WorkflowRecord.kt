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
 * [awaiting], [signals] and [wakeAt] are what let an instance stop for something that is not a
 * failure — a person approving a refund, a cool-off period — and outlive every process that touches
 * it while it waits.
 */
@Serializable
data class WorkflowRecord(
    val id: String,
    /** The workflow's name, which is how [com.strange.workflow.WorkflowEngine] finds the definition again. */
    val workflow: String,
    val status: WorkflowStatus,
    val context: JsonElement,
    val journal: List<JournalEntry> = emptyList(),
    /** The name of the signal this instance is stopped on, when [status] is [WorkflowStatus.Awaiting]. */
    val awaiting: String? = null,
    /**
     * Delivered payloads no `await` has consumed yet, by the name of the signal each belongs to.
     *
     * Keyed by name rather than held in one slot because **a payload can arrive before the instance
     * reaches the wait it answers**. A provider called back within milliseconds of the step that
     * asked it to is not a mistake, and refusing that delivery would make correctness depend on the
     * caller retrying until the instance happened to be parked. So a delivery is durable as soon as
     * it is accepted, and the wait picks up whichever payload is addressed to it whenever it gets
     * there.
     *
     * It is bounded by the number of distinct signals a definition declares, and only the last
     * payload under a name survives — a second approval overwrites the first rather than queueing
     * behind it, which is what "the approval" means. A payload delivered for a wait the run never
     * reaches stays here unread; it is a few hundred bytes on a record that is about to be purged.
     */
    val signals: Map<String, JsonElement> = emptyMap(),
    /**
     * When this instance is due to be looked at again — the end of a [WorkflowStatus.Sleeping]
     * pause, or the deadline on an [WorkflowStatus.Awaiting] one.
     *
     * Null while running means "as soon as nobody is holding it", which is a store's business.
     * Null while awaiting means **never**: see [isParked].
     */
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

    /** True when this instance stopped at [node] and is still stopped there. */
    fun paused(node: String): Boolean = latest(node)?.outcome == NodeOutcome.Paused

    /**
     * True when no amount of waiting will move this instance — only a signal or a cancel will.
     *
     * A store must keep these **out of its due-time index**. A worker that polled them would spend
     * its life offering the same instance to itself, finding the same signal still absent, and
     * parking it again — a busy loop whose cost grows with how patient the business process is.
     */
    val isParked: Boolean get() = status == WorkflowStatus.Awaiting && wakeAt == null
}
