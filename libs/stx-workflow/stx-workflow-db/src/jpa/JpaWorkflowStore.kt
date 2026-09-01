package com.strange.workflow.jpa

import com.strange.jpa.Jpa
import com.strange.jpa.query.find
import com.strange.jpa.session.transaction
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.store.WorkflowRecord
import com.strange.workflow.store.WorkflowStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Instances in a relational database: one row each, in the application's own schema.
 *
 * ```kotlin
 * val jpa = Jpa.connect(config, Order::class, WorkflowInstanceRow::class)
 * val engine = WorkflowEngine(JpaWorkflowStore(jpa)) { register(checkout) }
 * ```
 *
 * **Postgres, DB2 and MySQL come out of the same code**, because Hibernate Reactive names no
 * database of its own — it picks a driver from the URI's scheme, and so does this. There is no
 * dialect seam here and no SQL written twice; what would have needed one, an upsert and a
 * `skip locked` queue, is not how this store works. See the module README.
 *
 * It takes a [Jpa] it did not open and does not close, so the workflow rows live in the same pool
 * and the same transaction log as the application's own — which is the reason to choose this store
 * over Redis: an instance and the rows its steps wrote can be read back together, by anything that
 * speaks SQL, six months later.
 */
class JpaWorkflowStore(
    private val jpa: Jpa,
    /**
     * How long a lease is good for before the instance is assumed abandoned.
     *
     * It doubles as the visibility window: a row checkpointed less than this ago is due in the
     * future, so a worker leaves alone one somebody is plainly working on. An instance whose
     * process died becomes visible again one lease later.
     *
     * It is **not** a deadline on a step. The lease renews while the work runs, exactly as
     * `RedisLock` does; a deadline on user code is what `timeout` is for.
     */
    private val lease: Duration = 30.seconds,
    private val json: Json = jpa.config.json,
) : WorkflowStore {
    /** Who this process is, in `locked_by`. One per store, so a release can prove it holds the lease. */
    private val owner: String = UUID.randomUUID().toString()

    override suspend fun create(record: WorkflowRecord) {
        jpa.transaction { session ->
            require(session.find<WorkflowInstanceRow>(record.id) == null) {
                "workflow instance '${record.id}' already exists"
            }
            session.persist(row(record))
        }
    }

    override suspend fun load(id: String): WorkflowRecord? {
        val row = jpa.find<WorkflowInstanceRow>(id) ?: return null
        return json.decodeFromString(WorkflowRecord.serializer(), row.record).copy(version = row.version)
    }

    /**
     * One conditional `update`, and no entity loaded.
     *
     * **Not `@Version`.** Hibernate's optimistic locking is for an entity read into a session and
     * written back, and it signals a lost race by *throwing* at flush — so honouring this contract
     * with it would mean a select, a mutation, a rolled-back transaction and an exception caught
     * across it, to answer a boolean. The `where version = :expected` here is the same guarantee in
     * one statement, on every dialect, with `execute()`'s row count as the answer.
     */
    override suspend fun save(
        record: WorkflowRecord,
        expectedVersion: Long,
    ): Boolean {
        val now = Clock.System.now()
        return jpa.transaction { session ->
            session
                .mutate(
                    """
                    update WorkflowInstanceRow r
                       set r.record = :record, r.version = :next, r.dueAt = :dueAt, r.finishedAt = :finishedAt
                     where r.id = :id and r.version = :expected
                    """.trimIndent(),
                ).parameters(
                    "record" to encode(record),
                    "next" to expectedVersion + 1,
                    "dueAt" to dueAt(record, now),
                    "finishedAt" to finishedAt(record, now),
                    "id" to record.id,
                    "expected" to expectedVersion,
                ).execute()
        } == 1
    }

    override suspend fun runnable(
        now: Instant,
        limit: Int,
    ): List<String> {
        if (limit <= 0) return emptyList()
        return jpa.transaction { session ->
            session
                .query<String>(
                    "select r.id from WorkflowInstanceRow r where r.dueAt is not null and r.dueAt <= :now order by r.dueAt",
                ).parameter("now", now)
                .limit(limit)
                .list()
        }
    }

    /**
     * Takes the row's lease, or answers null.
     *
     * A lease column rather than `select … for update`, and the choice is what keeps this store one
     * implementation instead of two. A row lock lives inside a transaction, so holding one across a
     * step means holding a database connection for as long as the step runs — and `skip locked` is
     * spelled differently on every dialect. A lease is a column two `update`s agree about: it
     * survives the process that took it, it needs no open transaction, and it reads the same on
     * Postgres and DB2.
     *
     * It renews while [block] runs, for the reason `RedisLock` does: the lease says "this instance
     * is being advanced right now", not "this code is short".
     */
    override suspend fun <T> guarded(
        id: String,
        block: suspend () -> T,
    ): T? {
        if (!take(id)) return null
        return try {
            coroutineScope {
                val watchdog =
                    launch {
                        while (isActive) {
                            delay(lease / 3)
                            if (!renew(id)) break
                        }
                    }
                try {
                    block()
                } finally {
                    watchdog.cancel()
                }
            }
        } finally {
            // Releasing is a suspending database call, and a cancelled coroutine cannot make one —
            // without this, the scope dying mid-step, which is what a crash looks like, would throw
            // out of the finally and leave the row leased until it expired on its own.
            withContext(NonCancellable) { release(id) }
        }
    }

    /**
     * Deletes instances that finished before [before], and answers how many.
     *
     * **A relational table has no TTL**, which is the one thing the Redis store does that this one
     * cannot: there, a completed instance expires on its own. Here, retention is a job somebody
     * schedules — and that is the honest shape, because a library that quietly deleted rows out of
     * an application's own schema on a timer would be a surprise nobody signed up for.
     *
     * A `Failed` instance never gets a `finished_at` and is therefore never taken — see
     * [finishedAt].
     */
    suspend fun purge(before: Instant): Int =
        jpa.transaction { session ->
            session
                .mutate("delete from WorkflowInstanceRow r where r.finishedAt is not null and r.finishedAt < :before")
                .parameter("before", before)
                .execute()
        }

    /**
     * When this instance became eligible for retention, and null when it never does.
     *
     * Terminal, except [WorkflowStatus.Failed]. The exemption is the same one the Redis store makes
     * by withholding the TTL, for the same reason: a failed compensation is waiting for a person,
     * and deleting it removes the only description of what has to be fixed.
     */
    private fun finishedAt(
        record: WorkflowRecord,
        now: Instant,
    ): Instant? = now.takeIf { record.status.isTerminal && record.status != WorkflowStatus.Failed }

    private fun row(record: WorkflowRecord) =
        WorkflowInstanceRow(
            id = record.id,
            workflow = record.workflow,
            record = encode(record),
            version = record.version,
            dueAt = dueAt(record, record.updatedAt),
        )

    private fun encode(record: WorkflowRecord) = json.encodeToString(WorkflowRecord.serializer(), record)

    /**
     * When this instance should next be looked at, and null when the answer is never.
     *
     * The same three cases as the Redis store's score, which is the point — a second store that
     * answered "what is due" differently would be a second set of rules for the same engine.
     * A finished instance and one parked on a signal are both out of the index; everything else is
     * due at its `wakeAt`, or one lease after its last checkpoint.
     */
    private fun dueAt(
        record: WorkflowRecord,
        now: Instant,
    ): Instant? =
        when {
            record.status.isTerminal || record.isParked -> null
            else -> record.wakeAt ?: (now + lease)
        }

    private suspend fun take(id: String): Boolean {
        val now = Clock.System.now()
        return jpa.transaction { session ->
            session
                .mutate(
                    """
                    update WorkflowInstanceRow r set r.lockedBy = :owner, r.lockedUntil = :until
                     where r.id = :id and (r.lockedUntil is null or r.lockedUntil < :now)
                    """.trimIndent(),
                ).parameters("owner" to owner, "until" to now + lease, "id" to id, "now" to now)
                .execute()
        } == 1
    }

    private suspend fun renew(id: String): Boolean =
        jpa.transaction { session ->
            session
                .mutate("update WorkflowInstanceRow r set r.lockedUntil = :until where r.id = :id and r.lockedBy = :owner")
                .parameters("until" to Clock.System.now() + lease, "id" to id, "owner" to owner)
                .execute()
        } == 1

    private suspend fun release(id: String) {
        // The row may be gone — a purge, or a spec that dropped its schema — and a release that
        // failed is a lease that expires on its own a moment later. Nothing here is worth failing
        // a completed step for.
        runCatching {
            jpa.transaction { session ->
                session
                    .mutate(
                        "update WorkflowInstanceRow r set r.lockedBy = null, r.lockedUntil = null " +
                            "where r.id = :id and r.lockedBy = :owner",
                    ).parameters("id" to id, "owner" to owner)
                    .execute()
            }
        }.onFailure { if (it is CancellationException) throw it }
    }
}
