package com.softistx.migrations

import kotlin.time.Duration

/**
 * Everything this library throws on its own behalf.
 *
 * All four leave [MigrationRunner.run], and that is the design rather than an accident: this runs at
 * startup, before anything is serving, so an exception here is an application that does not start.
 * The runner it replaces logged each of these and carried on, which is how a deployment ends up
 * answering requests against a schema that was never migrated.
 */
sealed class MigrationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Two migrations claim the same version.
 *
 * Thrown when the runner is *built*, not when it runs, so a Spring context fails to refresh and a
 * Ktor server never binds. Which of the two would have run is arbitrary, and an arbitrary migration
 * order is worse than none — the other one would be recorded as having run under a description that
 * belongs to its rival.
 */
class DuplicateMigrationVersionException(
    val version: Long,
    val descriptions: List<String>,
) : MigrationException("two migrations claim version $version: ${descriptions.joinToString(", ")}")

/** [Migration.migrate] threw. The record is `FAILED`, everything after it is unrun, and this leaves `run()`. */
class MigrationFailedException(
    val version: Long,
    val description: String,
    cause: Throwable,
) : MigrationException("migration $version ($description) failed: ${cause.message ?: cause::class.java.simpleName}", cause)

/**
 * The ledger is not in a state anything may be applied to.
 *
 * Either a `FAILED` record from an earlier run, or a `RUNNING` one older than the runner's
 * `staleAfter` — a process that died holding it. Both need a person: the first because the next
 * migration was written against a change that did not happen, the second because whether the change
 * happened at all is a question only the database can answer.
 */
class MigrationHaltedException(
    val record: MigrationRecord,
) : MigrationException(
        "migration ${record.version} (${record.description}) is ${record.status}" +
            (record.failure?.let { ": $it" } ?: ", claimed by ${record.appliedBy} at ${record.at}") +
            " — nothing will run until that is resolved",
    )

/**
 * Somebody else has held the migration lock for longer than this run is willing to wait.
 *
 * Waiting is the point, and it is where this lock deliberately parts company with
 * `WorkflowStore.guarded`, which declines immediately. A fleet of workers declining a busy instance
 * goes off and does other work; a fleet of instances declining the migrations would go off and start
 * serving against a schema that does not exist yet.
 */
class MigrationLockTimeoutException(
    val waited: Duration,
) : MigrationException("another process has held the migration lock for more than $waited")

/**
 * A version already had a record when this run, holding the lock, went to claim it.
 *
 * Two things look like this and both stop the run. Either a previous process died between claiming
 * the version and finishing it, and its `RUNNING` row is not yet old enough for `staleAfter` to call
 * it abandoned — the ordinary case, and the same halt [MigrationHaltedException] gives once that
 * timeout passes. Or the lock genuinely did not hold, because a lease expired under a process that
 * was merely stalled, and two runs both believe they have it.
 *
 * Continuing is unsafe either way: whether the change landed is a question only the database can
 * answer, and the migrations after this one are written against it having landed.
 */
class MigrationConflictException(
    val version: Long,
) : MigrationException(
        "version $version already has a record, taken while this run held the lock — either a process " +
            "died mid-migration and its RUNNING row is younger than staleAfter, or a lease expired " +
            "under a process that is still alive",
    )
