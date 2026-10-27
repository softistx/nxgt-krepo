package com.softistx.workflow.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.softistx.mongo.query.byId
import com.softistx.mongo.query.update
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * The `updateOne`s a [com.softistx.common.coroutines.Lease] is made of, on the `lockedBy` /
 * `lockedUntil` pair of [WorkflowInstanceDocument].
 */
internal class MongoLeaseUpdates(
    private val instances: MongoCollection<WorkflowInstanceDocument>,
    private val lease: Duration,
) {
    /** Who this process is, in `lockedBy`. One per store, so a release can prove it holds the lease. */
    private val owner: String = UUID.randomUUID().toString()

    suspend fun take(id: String): Boolean {
        val now = Clock.System.now()
        val until = WorkflowInstanceDocument::lockedUntil.name
        return instances
            .update(
                Filters.and(byId(id), Filters.or(Filters.eq(until, null), Filters.lt(until, now))),
                Updates.combine(
                    Updates.set(WorkflowInstanceDocument::lockedBy.name, owner),
                    Updates.set(until, now + lease),
                ),
            ).matchedCount == 1L
    }

    suspend fun renew(id: String): Boolean =
        instances
            .update(
                mine(id),
                Updates.set(WorkflowInstanceDocument::lockedUntil.name, Clock.System.now() + lease),
            ).matchedCount == 1L

    suspend fun release(id: String) {
        instances.update(
            mine(id),
            Updates.combine(
                Updates.set(WorkflowInstanceDocument::lockedBy.name, null),
                Updates.set(WorkflowInstanceDocument::lockedUntil.name, null),
            ),
        )
    }

    private fun mine(id: String) = Filters.and(byId(id), Filters.eq(WorkflowInstanceDocument::lockedBy.name, owner))
}
