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
 * A version was recorded by somebody else while this run held the lock.
 *
 * Which means the lock did not hold — two processes both believed they had it. Stopping is the only
 * safe answer: the other one is applying the same migration right now, and continuing would apply
 * the ones after it against a half-made change.
 */
class MigrationConflictException(
    val version: Long,
) : MigrationException("version $version was claimed by another process while this one held the lock")
