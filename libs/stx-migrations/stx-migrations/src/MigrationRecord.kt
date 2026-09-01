package com.softistx.migrations

import kotlin.time.Instant

/**
 * Where a version got to.
 *
 * Three states and not four. The runner this library replaces had `PENDING`, `APPLIED` and `FAILED`
 * and no lock, which meant a process killed halfway through a migration left a ledger
 * indistinguishable from one where the migration had never been attempted — and the next startup
 * ran it again without a word. [RUNNING] is what closes that: it is written before the change and
 * moved after it, under a lock, so a record still saying [RUNNING] when nobody holds the lock is a
 * crash and is reported as one.
 *
 * There is no `PENDING`, because there is nothing for it to mean. A migration the ledger has never
 * heard of is pending; that is the absence of a record, and inventing a row to say so would be a
 * write before the lock is held.
 */
enum class MigrationStatus {
    /** Claimed and being applied right now — or by a process that died while applying it. */
    RUNNING,

    /** Applied. The runner skips it forever after. */
    APPLIED,

    /** It threw. Nothing after it ran, and nothing will until a person has looked. */
    FAILED,
}

/**
 * One row, or one document, in the ledger.
 *
 * [at] is when the version was claimed and never moves; [updatedAt] moves on every write, and the
 * distance between them on a [MigrationStatus.RUNNING] record is how the runner decides that a
 * holder is gone rather than slow.
 */
data class MigrationRecord(
    val version: Long,
    val description: String,
    val status: MigrationStatus,
    /** When the version was claimed. */
    val at: Instant,
    /** When this record was last written. */
    val updatedAt: Instant,
    /** Which process claimed it — see [migrationIdentity]. */
    val appliedBy: String? = null,
    /** The failure's message, when the status is [MigrationStatus.FAILED]. */
    val failure: String? = null,
    /** How long [Migration.migrate] took, once it has finished either way. */
    val durationMillis: Long? = null,
)
