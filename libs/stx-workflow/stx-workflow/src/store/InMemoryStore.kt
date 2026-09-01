package com.softistx.workflow.store

import com.softistx.common.coroutines.CoroutineSafeMap
import com.softistx.workflow.WorkflowStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * A store that keeps instances in this process and forgets them when it ends.
 *
 * It is not a stub. It is the reference implementation of the contract in [WorkflowStore] — the
 * conditional write really is conditional, [guarded] really declines rather than queues — which is
 * what lets the engine's own specs run without a container and finish in milliseconds. A
 * single-process worker or a test double is a fair use; anything that has to survive a restart
 * wants `RedisWorkflowStore`.
 *
 * [guarded] is a held-set rather than a `KeyedMutex`, because the two answer different questions. A
 * mutex makes the second caller **wait**; the contract here is that it is **told no** and goes to do
 * something else, which is what a second engine or a second worker should do with an instance
 * somebody is already advancing. Queueing behind it would mean every worker in a fleet eventually
 * parked on the same slow instance.
 */
class InMemoryStore : WorkflowStore {
    private val records = CoroutineSafeMap<String, WorkflowRecord>()
    private val held = CoroutineSafeMap<String, Unit>()

    override suspend fun create(record: WorkflowRecord) {
        records.update { map ->
            require(record.id !in map) { "workflow instance '${record.id}' already exists" }
            map[record.id] = record
        }
    }

    override suspend fun load(id: String): WorkflowRecord? = records.get(id)

    override suspend fun save(
        record: WorkflowRecord,
        expectedVersion: Long,
    ): Boolean =
        records.update { map ->
            val stored = map[record.id]
            if (stored == null || stored.version != expectedVersion) {
                false
            } else {
                map[record.id] = record.copy(version = expectedVersion + 1)
                true
            }
        }

    override suspend fun runnable(
        now: Instant,
        limit: Int,
    ): List<String> =
        records
            .snapshot()
            .values
            .asSequence()
            .filter { !it.status.isTerminal }
            .filter { !it.isParked }
            .filter { record -> record.wakeAt?.let { it <= now } != false }
            .sortedBy { it.updatedAt }
            .take(limit)
            .map { it.id }
            .toList()

    override suspend fun find(
        status: WorkflowStatus,
        limit: Int,
        offset: Int,
    ): List<WorkflowRecord> =
        records
            .snapshot()
            .values
            .asSequence()
            .filter { it.status == status }
            .sortedByDescending { it.updatedAt }
            .drop(offset)
            .take(limit)
            .toList()

    override suspend fun <T> guarded(
        id: String,
        block: suspend () -> T,
    ): T? {
        val taken =
            held.update { map ->
                if (id in map) {
                    false
                } else {
                    map[id] = Unit
                    true
                }
            }
        if (!taken) return null
        return try {
            block()
        } finally {
            // The release is a suspending call, and a cancelled coroutine cannot make one: without
            // this the scope being cancelled mid-step — which is exactly what a crash looks like —
            // would throw out of the finally and leave the instance held forever.
            withContext(NonCancellable) { held.remove(id) }
        }
    }
}
