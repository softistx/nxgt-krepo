package com.strange.workflow.mongo

import com.mongodb.ErrorCategory
import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.strange.common.serialization.lenientJson
import com.strange.mongo.collection
import com.strange.mongo.query.ID_FIELD
import com.strange.mongo.query.byId
import com.strange.mongo.query.ensureIndex
import com.strange.mongo.query.findAll
import com.strange.mongo.query.findById
import com.strange.mongo.query.insert
import com.strange.mongo.query.update
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.db.Lease
import com.strange.workflow.store.WorkflowRecord
import com.strange.workflow.store.WorkflowStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.bson.Document
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The collection instances live in, unless the caller names another. */
const val WORKFLOW_INSTANCES = "workflow_instances"

/**
 * Instances in MongoDB: one document each, with a TTL index doing the retention.
 *
 * ```kotlin
 * val store = MongoWorkflowStore(database)
 * val engine = WorkflowEngine(store) { register(checkout) }
 * ```
 *
 * **It is a suspending function, not a constructor**, because it creates the two indexes the store
 * needs before it hands one back — the due-time index that makes `runnable` a single ranged read
 * rather than a scan, and the TTL index that expires a finished instance. Doing that lazily on
 * first use would mean the first `runnable` of a fresh deployment scanning the collection, and
 * leaving it to the caller would mean a store that works and quietly degrades.
 */
@Suppress("ktlint:standard:function-naming")
suspend fun MongoWorkflowStore(
    database: MongoDatabase,
    collection: String = WORKFLOW_INSTANCES,
    lease: Duration = 30.seconds,
    retention: Duration? = 7.days,
    json: Json = lenientJson,
): MongoWorkflowStore {
    val instances = database.collection<WorkflowInstanceDocument>(collection)
    instances.ensureIndex(Indexes.ascending(WorkflowInstanceDocument::dueAt.name))
    // expireAfter(0) means "delete when the date in this field passes", rather than "…this long
    // after it". A document with no expiresAt is never taken, which is how everything still running
    // — and everything Failed — stays put.
    instances.ensureIndex(
        Indexes.ascending(WorkflowInstanceDocument::expiresAt.name),
        IndexOptions().expireAfter(0, TimeUnit.SECONDS),
    )
    return MongoWorkflowStore(instances, lease, retention, json)
}

/** See the factory of the same name. */
class MongoWorkflowStore internal constructor(
    private val instances: MongoCollection<WorkflowInstanceDocument>,
    private val lease: Duration,
    /**
     * How long a finished instance is kept, or null to keep it forever.
     *
     * A completed run is the audit trail somebody wants afterwards, so the default is a bound rather
     * than nothing. [WorkflowStatus.Failed] is exempt: it is waiting for a person, and expiring it
     * would delete the only description of what has to be fixed.
     */
    private val retention: Duration?,
    private val json: Json,
) : WorkflowStore {
    /** Who this process is, in `lockedBy`. One per store, so a release can prove it holds the lease. */
    private val owner: String = UUID.randomUUID().toString()

    private val guard = Lease(lease, ::take, ::renew, ::release)

    override suspend fun create(record: WorkflowRecord) {
        try {
            instances.insert(document(record, Clock.System.now()))
        } catch (e: MongoWriteException) {
            // A duplicate _id is the server refusing exactly what this method promises to refuse.
            // Anything else is a real write failure and is not ours to translate.
            if (e.error.category != ErrorCategory.DUPLICATE_KEY) throw e
            throw IllegalArgumentException("workflow instance '${record.id}' already exists", e)
        }
    }

    override suspend fun load(id: String): WorkflowRecord? {
        val document = instances.findById(id) ?: return null
        return json.decodeFromString(WorkflowRecord.serializer(), document.record).copy(version = document.version)
    }

    /**
     * One conditional `updateOne`, filtered on the stored version.
     *
     * The same shape as the relational store's `update`, and for the same reason: the contract wants
     * a boolean, and `matchedCount` is one. A `findOneAndUpdate` would answer it too, at the cost of
     * shipping the whole document back to decide something the count already decided.
     */
    override suspend fun save(
        record: WorkflowRecord,
        expectedVersion: Long,
    ): Boolean {
        val now = Clock.System.now()
        val updated = document(record, now).copy(version = expectedVersion + 1)
        return instances
            .update(
                Filters.and(byId(record.id), Filters.eq(WorkflowInstanceDocument::version.name, expectedVersion)),
                Updates.combine(
                    Updates.set(WorkflowInstanceDocument::record.name, updated.record),
                    Updates.set(WorkflowInstanceDocument::version.name, updated.version),
                    Updates.set(WorkflowInstanceDocument::dueAt.name, updated.dueAt),
                    Updates.set(WorkflowInstanceDocument::expiresAt.name, updated.expiresAt),
                ),
            ).matchedCount == 1L
    }

    override suspend fun runnable(
        now: Instant,
        limit: Int,
    ): List<String> {
        if (limit <= 0) return emptyList()
        val due = WorkflowInstanceDocument::dueAt.name
        return instances
            // Projected to the id alone, which will not decode as a WorkflowInstanceDocument — so
            // the flow reads raw documents, the way stx-mongo's own `existingIds` does.
            .withDocumentClass<Document>()
            .findAll(Filters.and(Filters.ne(due, null), Filters.lte(due, now)))
            .sort(Indexes.ascending(due))
            .limit(limit)
            .projection(Projections.include(ID_FIELD))
            .map { it.getString(ID_FIELD) }
            .toList()
    }

    /**
     * A lease pair, because MongoDB has no lock to borrow.
     *
     * Two fields an `updateOne` sets and another clears — the same two the relational store uses, and
     * the same [Lease] policy around them. The alternative here would have been a lock collection of
     * its own, which is these two fields in a second document plus the problem of keeping the two
     * documents in step.
     */
    override suspend fun <T> guarded(
        id: String,
        block: suspend () -> T,
    ): T? = guard.guard(id, block)

    private fun document(
        record: WorkflowRecord,
        now: Instant,
    ) = WorkflowInstanceDocument(
        id = record.id,
        workflow = record.workflow,
        record = json.encodeToString(WorkflowRecord.serializer(), record),
        version = record.version,
        dueAt = dueAt(record, now),
        expiresAt = expiresAt(record, now),
    )

    /**
     * When this instance should next be looked at, and null when the answer is never.
     *
     * The same three cases as `RedisWorkflowStore.due` and `JpaWorkflowStore.dueAt`, which is the
     * point — a store that answered "what is due" differently would be a second set of rules for the
     * same engine. A finished instance and one parked on a signal are both out of the index;
     * everything else is due at its `wakeAt`, or one lease after its last checkpoint.
     */
    private fun dueAt(
        record: WorkflowRecord,
        now: Instant,
    ): Instant? =
        when {
            record.status.isTerminal || record.isParked -> null
            else -> record.wakeAt ?: (now + lease)
        }

    private fun expiresAt(
        record: WorkflowRecord,
        now: Instant,
    ): Instant? = retention?.takeIf { record.status.isTerminal && record.status != WorkflowStatus.Failed }?.let { now + it }

    private suspend fun take(id: String): Boolean {
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

    private suspend fun renew(id: String): Boolean =
        instances
            .update(
                mine(id),
                Updates.set(WorkflowInstanceDocument::lockedUntil.name, Clock.System.now() + lease),
            ).matchedCount == 1L

    private suspend fun release(id: String) {
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
