package com.softistx.workflow.mongo

import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.store.WorkflowRecord
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Instant

internal fun workflowDocument(
    record: WorkflowRecord,
    now: Instant,
    json: Json,
    lease: Duration,
    retention: Duration?,
) = WorkflowInstanceDocument(
    id = record.id,
    workflow = record.workflow,
    record = json.encodeToString(WorkflowRecord.serializer(), record),
    status = record.status.name,
    updatedAt = record.updatedAt,
    version = record.version,
    dueAt = dueAt(record, now, lease),
    expiresAt = expiresAt(record, now, retention),
)

internal fun decode(
    document: WorkflowInstanceDocument,
    json: Json,
): WorkflowRecord = json.decodeFromString(WorkflowRecord.serializer(), document.record).copy(version = document.version)

/**
 * When this instance should next be looked at, and null when the answer is never.
 *
 * The same three cases as `RedisWorkflowStore.due` and `dueAt` in `jpa/WorkflowRowMapping.kt`, which is the
 * point — a store that answered "what is due" differently would be a second set of rules for the
 * same engine. A finished instance and one parked on a signal are both out of the index;
 * everything else is due at its `wakeAt`, or one lease after its last checkpoint.
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

internal fun expiresAt(
    record: WorkflowRecord,
    now: Instant,
    retention: Duration?,
): Instant? = retention?.takeIf { record.status.isTerminal && record.status != WorkflowStatus.Failed }?.let { now + it }
