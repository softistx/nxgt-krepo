package com.softistx.workflow.engine

import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowStatus
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration.Companion.milliseconds

/**
 * Writes the payload into the record, under the lock the caller already holds.
 *
 * It wakes the instance only when the instance was parked on *this* signal. A delivery that
 * arrived early, or for a later wait, leaves the status alone: the run that follows walks the
 * declaration as it would have anyway, and either reaches the wait and reads the payload or
 * parks again where it was. Waking an instance parked on a different signal would step over
 * that wait, which is the one thing an early delivery must not be allowed to do.
 */
internal suspend fun <C> WorkflowEngine.deliver(
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
