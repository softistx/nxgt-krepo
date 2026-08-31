package com.strange.workflow.engine

import com.strange.workflow.Workflow
import com.strange.workflow.WorkflowConflictException
import com.strange.workflow.WorkflowError
import com.strange.workflow.dsl.StepScope
import com.strange.workflow.store.JournalEntry
import com.strange.workflow.store.NodeOutcome
import com.strange.workflow.store.WorkflowRecord
import com.strange.workflow.store.WorkflowStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * One instance being advanced, and the only thing that writes to it.
 *
 * Everything the engine does to a record goes through [checkpoint], which is what keeps the version
 * check in a single place: the whole design rests on a write being refused when somebody else got
 * there first, and a second write path would be the way that quietly stops being true.
 *
 * It holds the decoded [context] beside the encoded one in [record] because the two are needed at
 * different moments — the steps want `C`, the store wants JSON — and re-encoding on every read
 * would make the context's serializer run once per node for nothing.
 */
internal class Run<C>(
    val store: WorkflowStore,
    val json: Json,
    val workflow: Workflow<C>,
    var record: WorkflowRecord,
) {
    var context: C = json.decodeFromJsonElement(workflow.serializer, record.context)

    val id: String get() = record.id

    fun scope(
        node: String,
        attempt: Int,
        startedAt: Instant,
    ) = StepScope(context, record.id, workflow.name, node, attempt, startedAt)

    fun encoded(): JsonElement = json.encodeToJsonElement(workflow.serializer, context)

    /**
     * Writes the record, or gives up.
     *
     * A refused write means another engine advanced this instance while this one was working, so
     * this one's journal is a fork of the truth. There is nothing to merge — it has already run
     * effects the other engine does not know about — so it stops and says so.
     */
    suspend fun checkpoint(update: (WorkflowRecord) -> WorkflowRecord) {
        val expected = record.version
        val next = update(record).copy(updatedAt = Clock.System.now())
        if (!store.save(next, expected)) throw WorkflowConflictException(record.id)
        record = next.copy(version = expected + 1)
    }

    /** Appends one line to the journal. */
    suspend fun journal(
        node: String,
        outcome: NodeOutcome,
        attempts: Int,
        value: JsonElement? = null,
        error: WorkflowError? = null,
        context: JsonElement? = null,
    ) {
        checkpoint { record ->
            record.copy(
                context = context ?: record.context,
                journal = record.journal + JournalEntry(node, outcome, attempts, value, error, Clock.System.now()),
            )
        }
    }
}

/** A node gave up. Carries the qualified name so the journal and the unwind know what failed. */
internal class NodeFailed(
    val node: String,
    override val cause: Throwable,
    val attempts: Int,
) : Exception(cause)

/** One leg of a fan-out gave up. Named separately so a retry of the fan-out can tell them apart. */
internal class LegFailed(
    val leg: String,
    override val cause: Throwable,
    val attempts: Int,
) : Exception(cause)
