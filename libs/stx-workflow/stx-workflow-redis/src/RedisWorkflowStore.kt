package com.strange.workflow.redis

import com.strange.redis.Redis
import com.strange.redis.RedisValueException
import com.strange.redis.lock.RedisLock
import com.strange.workflow.store.WorkflowRecord
import com.strange.workflow.store.WorkflowStore
import io.lettuce.core.Limit
import io.lettuce.core.Range
import io.lettuce.core.ScriptOutputType
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Instances in Redis: one hash each, one sorted set for the ones that are due, and a lock apiece.
 *
 * ```kotlin
 * val engine = WorkflowEngine(RedisWorkflowStore(redis)) { register(checkout) }
 * ```
 *
 * It takes a connection it did not open and does not close — whoever created the [Redis] closes it,
 * which is what lets one connection serve a cache, a lock and this at the same time.
 */
class RedisWorkflowStore(
    private val redis: Redis,
    /**
     * How long a lock is good for before it is assumed abandoned.
     *
     * It doubles as the visibility window: an instance that was checkpointed less than this ago is
     * scored into the future, so a worker leaves it alone rather than trying a lock somebody
     * obviously holds. An instance whose process died becomes visible again one lease later.
     *
     * It is **not** a deadline on a step. `RedisLock` renews while the work runs, so a step slower
     * than this keeps its lock; a deadline on user code is what `timeout` is for.
     */
    private val lease: Duration = 30.seconds,
    /**
     * How long a finished instance is kept.
     *
     * A completed run is the audit trail somebody will want afterwards, and an engine whose
     * finished instances vanish is one that cannot be debugged — but Redis holds it in memory, so
     * the default is a bound rather than forever. `null` keeps them.
     *
     * A [com.strange.workflow.WorkflowStatus.Failed] instance is exempt: it is waiting for a person,
     * and expiring it would delete the only description of what needs fixing.
     */
    private val retention: Duration? = 7.days,
    private val json: Json = redis.json,
) : WorkflowStore {
    override suspend fun create(record: WorkflowRecord) {
        val written =
            redis.commands.eval<Long>(
                StoreScripts.CREATE,
                ScriptOutputType.INTEGER,
                arrayOf(redis.instanceKey(record.id), redis.runnableKey()),
                encode(record),
                record.version.toString(),
                score(record).toString(),
                record.id,
            )
        require(written == 1L) { "workflow instance '${record.id}' already exists" }
    }

    override suspend fun load(id: String): WorkflowRecord? {
        val key = redis.instanceKey(id)
        val stored = redis.commands.hget(key, "record") ?: return null
        val version = redis.commands.hget(key, "version")?.toLongOrNull() ?: 0L
        return try {
            json.decodeFromString(WorkflowRecord.serializer(), stored).copy(version = version)
        } catch (e: SerializationException) {
            throw RedisValueException("workflow instance at '$key' did not decode", e)
        }
    }

    override suspend fun save(
        record: WorkflowRecord,
        expectedVersion: Long,
    ): Boolean {
        // Out of the index, and for two different reasons. A terminal instance is done and takes a
        // retention TTL with it; a parked one is very much alive but nothing except a signal will
        // move it, so polling for it would be a worker in a loop with itself. Neither gets a score.
        val due = if (record.status.isTerminal || record.isParked) "" else score(record).toString()
        val ttl = if (record.status.isTerminal) terminalTtl(record) else ""
        return redis.commands.eval<Long>(
            StoreScripts.SAVE,
            ScriptOutputType.INTEGER,
            arrayOf(redis.instanceKey(record.id), redis.runnableKey()),
            encode(record),
            expectedVersion.toString(),
            (expectedVersion + 1).toString(),
            due,
            record.id,
            ttl,
        ) == 1L
    }

    override suspend fun runnable(
        now: Instant,
        limit: Int,
    ): List<String> {
        if (limit <= 0) return emptyList()
        return redis.commands
            .zrangebyscore(
                redis.runnableKey(),
                Range.create(0, now.toEpochMilliseconds()),
                Limit.create(0, limit.toLong()),
            ).toList()
    }

    /**
     * Runs [block] as the only advancer of [id], or answers null.
     *
     * `wait = ZERO` because a caller that cannot get in should go and do something else: whoever
     * holds it is already advancing this instance, and queueing behind them would end with every
     * worker in a fleet parked on the same slow step. `renew = true` — the default — is what keeps a
     * step longer than [lease] from silently losing the lock underneath itself.
     */
    override suspend fun <T> guarded(
        id: String,
        block: suspend () -> T,
    ): T? = RedisLock(redis, instanceLockName(id), lease).withLockOrNull { block() }

    private fun encode(record: WorkflowRecord) = json.encodeToString(WorkflowRecord.serializer(), record)

    /**
     * When this instance should next be looked at.
     *
     * A running one is scored a lease into the future, so it is not offered while somebody is
     * plainly working on it; if that somebody dies, the score passes and it is offered again. One
     * waiting on a time — a `sleep`, or an `await` with a deadline — is scored at that time, which
     * is the same question asked of the same sorted set and is why it is a sorted set.
     */
    private fun score(record: WorkflowRecord): Long = (record.wakeAt ?: (record.updatedAt + lease)).toEpochMilliseconds()

    private fun terminalTtl(record: WorkflowRecord): String =
        when {
            retention == null -> ""
            record.status == com.strange.workflow.WorkflowStatus.Failed -> ""
            else -> retention.inWholeMilliseconds.toString()
        }
}
