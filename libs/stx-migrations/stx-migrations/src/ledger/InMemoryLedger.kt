package com.softistx.migrations.ledger

import com.softistx.common.coroutines.CoroutineSafeMap
import com.softistx.migrations.MigrationRecord
import com.softistx.migrations.MigrationStatus
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * A ledger that keeps its records in this process and forgets them when it ends.
 *
 * It is not a stub. It is the reference implementation of [MigrationLedger] — [claim] really does
 * refuse a version that is already there, [guarded] really is told no rather than made to wait —
 * which is what lets the runner's own specs run without a container and finish in milliseconds. A
 * single-process tool or a test double is a fair use; anything with a second instance of the
 * application wants a real ledger from `stx-migrations-db`.
 *
 * **The lock has no lease and needs none.** A lease exists to expire when the process holding it
 * dies, and a holder here is a coroutine in this JVM: when it is gone the flag is gone with it,
 * because [Mutex.unlock] does not suspend and so a `finally` always reaches it — even on
 * cancellation, which is where a real ledger has to reach for `NonCancellable`. What that also means
 * is that this ledger can never produce a stale [MigrationStatus.RUNNING] record on its own: the one
 * part of the contract only a store outside this process can exercise.
 */
class InMemoryLedger : MigrationLedger {
    private val records = CoroutineSafeMap<Long, MigrationRecord>()
    private val lock = Mutex()

    override suspend fun prepare() = Unit

    override suspend fun find(version: Long): MigrationRecord? = records.get(version)

    override suspend fun claim(record: MigrationRecord): Boolean =
        records.update { map ->
            if (record.version in map) {
                false
            } else {
                map[record.version] = record
                true
            }
        }

    override suspend fun update(record: MigrationRecord) {
        records.put(record.version, record)
    }

    override suspend fun blocking(staleAfter: Duration): MigrationRecord? {
        val cutoff = Clock.System.now() - staleAfter
        return records
            .snapshot()
            .values
            .filter {
                it.status == MigrationStatus.FAILED ||
                    (it.status == MigrationStatus.RUNNING && it.updatedAt < cutoff)
            }.minByOrNull { it.version }
    }

    override suspend fun all(): List<MigrationRecord> = records.snapshot().values.sortedBy { it.version }

    /**
     * A `Mutex` used as a flag rather than as a queue: [Mutex.tryLock] is the decline the contract
     * asks for, where `withLock` would make the second caller wait.
     */
    override suspend fun <T> guarded(block: suspend () -> T): T? {
        if (!lock.tryLock()) return null
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}
