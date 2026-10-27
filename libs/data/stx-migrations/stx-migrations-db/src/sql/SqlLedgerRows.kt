package com.softistx.migrations.db.sql

import com.softistx.migrations.MigrationRecord
import com.softistx.migrations.MigrationStatus
import kotlinx.coroutines.future.await
import org.hibernate.reactive.pool.ReactiveConnection
import kotlin.time.Instant

/** The column list, written once so [toRecord]'s positions cannot drift from it. */
internal const val SELECT =
    "select version, description, status, applied_by, failure, duration_ms, started_at, updated_at"

internal const val DESCRIPTION_LIMIT = 500
internal const val FAILURE_LIMIT = 2000
internal const val OWNER_LIMIT = 200

internal suspend fun ReactiveConnection.rows(
    sql: String,
    vararg parameters: Any?,
): List<MigrationRecord> {
    val result = if (parameters.isEmpty()) select(sql).await() else select(sql, arrayOf(*parameters)).await()
    return buildList { while (result.hasNext()) add(result.next().toRecord()) }
}

internal fun MigrationRecord.toRow(): Array<Any?> =
    arrayOf<Any?>(
        version,
        description.take(DESCRIPTION_LIMIT),
        status.name,
        appliedBy?.take(OWNER_LIMIT),
        failure?.take(FAILURE_LIMIT),
        durationMillis,
        at.toEpochMilliseconds(),
        updatedAt.toEpochMilliseconds(),
    )

private fun Array<out Any?>.toRecord(): MigrationRecord =
    MigrationRecord(
        version = this[0] as Long,
        description = this[1] as String,
        status = MigrationStatus.valueOf(this[2] as String),
        at = Instant.fromEpochMilliseconds(this[6] as Long),
        updatedAt = Instant.fromEpochMilliseconds(this[7] as Long),
        appliedBy = this[3] as String?,
        failure = this[4] as String?,
        durationMillis = this[5] as Long?,
    )
