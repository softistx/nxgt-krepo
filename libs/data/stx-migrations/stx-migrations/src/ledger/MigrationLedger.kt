package com.softistx.migrations.ledger

import com.softistx.migrations.MigrationRecord
import com.softistx.migrations.MigrationStatus
import kotlin.time.Duration

/**
 * Where the ledger lives, and the lock around it.
 *
 * Seven members, and every one of them is something a store does differently — an insert that must
 * fail on a duplicate, a conditional write on two fields, a read filtered by status and age.
 * Everything else is the runner: the ordering, the duplicate-version check, the halt rule, the
 * waiting. Nothing here names MongoDB or SQL, and this module depends on neither; the
 * implementations live in `stx-migrations-db`, so the day a third store arrives nothing here moves.
 *
 * **Nothing here is `AutoCloseable`.** A ledger takes a connection it did not open — the same rule
 * `stx-workflow-db`'s stores are written to, and for the same reason: the application that built the
 * `MongoDatabase` or the `Jpa` is the one that closes it, and a ledger that closed it would close
 * everything else using it too.
 *
 * **[prepare] is called by the runner**, not by whoever built the ledger, so nobody can forget it.
 * That is why the implementations are ordinary constructors rather than the suspending factory
 * functions `MongoWorkflowStore` uses: there is no index to create before the object is usable,
 * because creating it is the first thing `run()` does.
 */
interface MigrationLedger {
    /**
     * Creates whatever the ledger needs — table, index, lock row — and does nothing when it is
     * already there.
     *
     * Called on every run, so it has to be idempotent in the store's own terms rather than in a
     * "we checked first" sense: `create table if not exists`, an index creation that accepts an
     * existing index of the same name, an insert whose duplicate-key error is swallowed.
     */
    suspend fun prepare()

    /** The record for [version], or null when the ledger has never heard of it. */
    suspend fun find(version: Long): MigrationRecord?

    /**
     * Writes [record] if and only if its version is not already recorded, and answers whether it
     * did.
     *
     * An insert, never an upsert and never a save: false means somebody else got there, and the
     * caller must not overwrite what they wrote. Under the migration lock this should be impossible,
     * which is exactly why the answer is worth having — see
     * [MigrationConflictException][com.softistx.migrations.MigrationConflictException].
     */
    suspend fun claim(record: MigrationRecord): Boolean

    /** Overwrites the record for `record.version`, which [claim] has already put there. */
    suspend fun update(record: MigrationRecord)

    /**
     * The record that stops anything from running, or null when nothing does.
     *
     * Two cases, both fail-closed: a [MigrationStatus.FAILED] record from any earlier run, and a
     * [MigrationStatus.RUNNING] one whose `updatedAt` is older than [staleAfter] — a process that
     * died holding it.
     *
     * **Only ever called while the lock is held**, which is what makes "stale" mean something: a
     * process that were still alive would still be holding the lock, so this call would not be
     * happening. The lowest version wins when there is more than one, because that is the one to
     * look at first.
     */
    suspend fun blocking(staleAfter: Duration): MigrationRecord?

    /** Every record, lowest version first. */
    suspend fun all(): List<MigrationRecord>

    /**
     * Runs [block] holding the migration lock, or answers null without running it when somebody else
     * holds it.
     *
     * No `id` parameter: there is one ledger and one lock over it, unlike `WorkflowStore.guarded`
     * where the lock is per instance. Null is *somebody else is migrating*, and the runner's answer
     * to it is to wait and ask again — deciding to wait is the caller's, which is why this declines
     * rather than queueing.
     *
     * An implementation over a lease has to renew it while [block] runs, because a migration is
     * allowed to take minutes.
     */
    suspend fun <T> guarded(block: suspend () -> T): T?
}
