package com.softistx.migrations.db.mongo

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.migrations.MigrationContext
import com.softistx.migrations.MigrationRunner
import com.softistx.migrations.migrationIdentity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A runner over [database], for the migrations that take a [MongoDatabase].
 *
 * ```kotlin
 * MongoMigrations(database, listOf(V1Seed(), V2Tags())).run()
 * ```
 *
 * Selection is **by construction**, not by a `when` on a URI scheme: an application that calls this
 * has a Mongo database in hand and is holding migrations the compiler already agreed are Mongo ones.
 * `stx-workflow-db` chose the same shape for the same reason.
 *
 * The ledger and the runner are given **one** identity, so the `lockedBy` on the lock document and
 * the `appliedBy` on the record that was written under it name the same run.
 */
@Suppress("ktlint:standard:function-naming")
fun MongoMigrations(
    database: MongoDatabase,
    migrations: List<MongoMigration>,
    /** The record collection. The lock lives in `<collection>_lock`. */
    collection: String = "stx_migrations",
    lease: Duration = 5.minutes,
    lockTimeout: Duration = 5.minutes,
    lockPoll: Duration = 1.seconds,
    staleAfter: Duration = 15.minutes,
    identity: String = migrationIdentity(),
): MigrationRunner<MongoDatabase> =
    MigrationRunner(
        ledger = MongoMigrationLedger(database, collection, lease, identity),
        migrations = migrations,
        context = MigrationContext { it(database) },
        lockTimeout = lockTimeout,
        lockPoll = lockPoll,
        staleAfter = staleAfter,
        identity = identity,
    )
