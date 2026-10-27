package com.softistx.workflow.jpa

import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.store.WorkflowRecord
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * When this instance became eligible for retention, and null when it never does.
 *
 * Terminal, except [WorkflowStatus.Failed]. The exemption is the same one the Redis store makes
 * by withholding the TTL, for the same reason: a failed compensation is waiting for a person,
 * and deleting it removes the only description of what has to be fixed.
 */
internal fun finishedAt(
    record: WorkflowRecord,
    now: Instant,
): Instant? = now.takeIf { record.status.isTerminal && record.status != WorkflowStatus.Failed }

internal fun workflowRow(
    record: WorkflowRecord,
    json: Json,
    lease: Duration,
) = WorkflowInstanceRow(
    id = record.id,
    workflow = record.workflow,
    record = encode(record, json),
    status = record.status.name,
    updatedAt = record.updatedAt,
    version = record.version,
    dueAt = dueAt(record, record.updatedAt, lease),
)

internal fun encode(
    record: WorkflowRecord,
    json: Json,
) = json.encodeToString(WorkflowRecord.serializer(), record)

internal fun decode(
    row: WorkflowInstanceRow,
    json: Json,
): WorkflowRecord = json.decodeFromString(WorkflowRecord.serializer(), row.record).copy(version = row.version)

/**
 * When this instance should next be looked at, and null when the answer is never.
 *
 * The same three cases as the Redis store's score, which is the point — a second store that
 * answered "what is due" differently would be a second set of rules for the same engine.
 * A finished instance and one parked on a signal are both out of the index; everything else is
 * due at its `wakeAt`, or one lease after its last checkpoint.
 */
internal fun dueAt(
    record: WorkflowRecord,
    now: Instant,
    lease: Duration,
): Instant? =
    when {
        record.status.isTerminal || record.isParked -> null
        else -> record.wakeAt ?: (now + lease)
    }
