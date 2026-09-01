package com.softistx.workflow.redis

import com.softistx.redis.Redis
import com.softistx.redis.RedisValueException
import com.softistx.redis.lock.RedisLock
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.store.WorkflowRecord
import com.softistx.workflow.store.WorkflowStore
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
     * A [com.softistx.workflow.WorkflowStatus.Failed] instance is exempt: it is waiting for a person,
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
                due(record),
                record.id,
                record.status.name,
                redis.statusPrefix(),
                record.updatedAt.toEpochMilliseconds().toString(),
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
        val due = due(record)
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
            record.status.name,
            redis.statusPrefix(),
            record.updatedAt.toEpochMilliseconds().toString(),
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
     * A page of the instances in [status], newest first.
     *
     * `ZREVRANGEBYSCORE` gives the ids in one round trip; the records are then read one hash at a
     * time, because a page is tens of instances and a pipeline for that is machinery without a
     * reason.
     *
     * **It prunes as it reads.** A terminal instance's hash expires under the retention TTL, and
     * nothing expires an entry in a sorted set — so an id whose hash is gone is an entry that
     * outlived what it points at, and this removes it. `Failed` is exempt from the TTL, so the one
     * index an operator actually reads never rots in the first place; this keeps the others honest.
     */
    override suspend fun find(
        status: WorkflowStatus,
        limit: Int,
        offset: Int,
    ): List<WorkflowRecord> {
        if (limit <= 0) return emptyList()
        val index = redis.statusPrefix() + status.name
        val ids =
            redis.commands
                .zrevrangebyscore(index, Range.unbounded<Long>(), Limit.create(offset.toLong(), limit.toLong()))
                .toList()
        return ids.mapNotNull { id ->
            load(id) ?: null.also { redis.commands.zrem(index, id) }
        }
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
     * When this instance should next be looked at, as a score — and the empty string when the answer
     * is never.
     *
     * A running one is scored a lease into the future, so it is not offered while somebody is
     * plainly working on it; if that somebody dies, the score passes and it is offered again. One
     * waiting on a time — a `sleep`, or an `await` with a deadline — is scored at that time, which
     * is the same question asked of the same sorted set and is why it is a sorted set.
     *
     * Out of the index entirely, and for two different reasons. A terminal instance is done and
     * takes a retention TTL with it; a parked one is very much alive but nothing except a signal
     * will move it, so polling for it would be a worker in a loop with itself.
     *
     * Both write paths ask this one function, which the shared store contract is what caught: the
     * two used to disagree, and only a caller that created a non-running instance would have found
     * out. `JpaWorkflowStore.dueAt` is the same three cases in the relational store.
     */
    private fun due(record: WorkflowRecord): String =
        when {
            record.status.isTerminal || record.isParked -> ""
            else -> (record.wakeAt ?: (record.updatedAt + lease)).toEpochMilliseconds().toString()
        }

    private fun terminalTtl(record: WorkflowRecord): String =
        when {
            retention == null -> ""
            record.status == WorkflowStatus.Failed -> ""
            else -> retention.inWholeMilliseconds.toString()
        }
}
