package com.softistx.migrations

import com.softistx.migrations.ledger.MigrationLedger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Applies the migrations that have not been applied, and refuses to start when it cannot.
 *
 * ```kotlin
 * val runner = SqlMigrations(jpa, listOf(V1Orders(), V2OrderIndex()))
 * val ledger = runner.run()      // or the application does not start
 * ```
 *
 * **This is a gate, not a job.** It runs in the process that is about to serve, on the thread
 * starting it, and every way it can go wrong leaves `run()` as a [MigrationException]. The runner it
 * replaces was a suspending `ApplicationReadyEvent` listener, which Spring does not wait for — the
 * port opened while migrations were still running, and the example application worked around it by
 * polling the ledger from its own specs.
 *
 * **What it does, in order.** Prepare the ledger. Take the lock, waiting up to [lockTimeout] for it.
 * Refuse if anything in the ledger is `FAILED`, or `RUNNING` and older than [staleAfter]. Then, per
 * migration in version order: skip it if it is `APPLIED`, otherwise claim it as `RUNNING`, apply it,
 * and mark it `APPLIED` — or `FAILED` and stop.
 *
 * **Stopping at the first failure is the whole point.** Migrations are written against the state the
 * previous one left, so continuing past a failure applies a change to a database that is not in the
 * shape it expects.
 */
class MigrationRunner<C>(
    private val ledger: MigrationLedger,
    migrations: List<Migration<C>>,
    private val context: MigrationContext<C>,
    /**
     * How long to wait for another process to finish migrating before giving up.
     *
     * Generous on purpose: everyone waiting is waiting on the same one-off outcome, and an instance
     * that gave up and started serving would serve against a schema that does not exist yet.
     */
    private val lockTimeout: Duration = 5.minutes,
    /** How long to wait between attempts on the lock. */
    private val lockPoll: Duration = 1.seconds,
    /**
     * How old a `RUNNING` record has to be before it is read as a process that died rather than one
     * that is slow.
     *
     * Only consulted while this run holds the lock, so a live holder is never mistaken for a dead
     * one — it would still have the lock, and this call would not be happening.
     */
    private val staleAfter: Duration = 15.minutes,
    /** Recorded in `appliedBy`, and used as the lock owner by the ledgers that take one. */
    private val identity: String = migrationIdentity(),
) {
    /** The migrations in the order they will run. */
    private val plan: List<Migration<C>> = migrations.sortedBy { it.version }

    // Checked here, in the constructor, rather than in run(): a duplicate version is a mistake in the
    // code, and a Spring context that fails to refresh or a Ktor server that never binds says so at
    // the only moment it is cheap to fix. stx-workflow's Declaration rejects two nodes at one `order`
    // the same way and for the same reason.
    init {
        plan.groupBy { it.version }.entries.firstOrNull { it.value.size > 1 }?.let { (version, rivals) ->
            throw DuplicateMigrationVersionException(version, rivals.map { it.description })
        }
    }

    /**
     * Applies what is outstanding and answers with the whole ledger.
     *
     * The answer is every record the ledger holds, not just the ones this call wrote — what a caller
     * wants to log or assert on is the state of the schema, and *nothing to do* is an answer worth
     * being able to see.
     */
    suspend fun run(): List<MigrationRecord> {
        ledger.prepare()
        if (plan.isEmpty()) return ledger.all()
        return locked()
    }

    /** Takes the lock, waiting for it, and applies everything inside it. */
    private suspend fun locked(): List<MigrationRecord> {
        val started = TimeSource.Monotonic.markNow()
        while (true) {
            ledger.guarded { apply() }?.let { return it }
            if (started.elapsedNow() >= lockTimeout) throw MigrationLockTimeoutException(lockTimeout)
            delay(lockPoll)
        }
    }

    private suspend fun apply(): List<MigrationRecord> {
        ledger.blocking(staleAfter)?.let { throw MigrationHaltedException(it) }
        plan.forEach { migration ->
            if (ledger.find(migration.version)?.status != MigrationStatus.APPLIED) applyOne(migration)
        }
        return ledger.all()
    }

    private suspend fun applyOne(migration: Migration<C>) {
        val now = Clock.System.now()
        val claimed =
            MigrationRecord(
                version = migration.version,
                description = migration.description,
                status = MigrationStatus.RUNNING,
                at = now,
                updatedAt = now,
                appliedBy = identity,
            )
        if (!ledger.claim(claimed)) throw MigrationConflictException(migration.version)

        val started = TimeSource.Monotonic.markNow()
        try {
            context.use { migration.migrate(it) }
        } catch (cancellation: CancellationException) {
            // The record stays RUNNING, which is the truth: this process stopped mid-migration and
            // whether the change landed is a question only the database can answer. Recording FAILED
            // here would claim we know it did not.
            throw cancellation
        } catch (failure: Throwable) {
            ledger.update(claimed.finished(MigrationStatus.FAILED, started, failure.message ?: failure::class.java.simpleName))
            throw MigrationFailedException(migration.version, migration.description, failure)
        }
        ledger.update(claimed.finished(MigrationStatus.APPLIED, started))
    }

    private fun MigrationRecord.finished(
        status: MigrationStatus,
        started: TimeSource.Monotonic.ValueTimeMark,
        failure: String? = null,
    ): MigrationRecord =
        copy(
            status = status,
            failure = failure,
            updatedAt = Clock.System.now(),
            durationMillis = started.elapsedNow().inWholeMilliseconds,
        )
}
