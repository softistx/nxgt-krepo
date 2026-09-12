package com.softistx.migrations.db.sql

import com.softistx.jpa.Jpa
import com.softistx.jpa.session.connection
import com.softistx.migrations.MigrationContext
import com.softistx.migrations.MigrationRunner
import com.softistx.migrations.migrationIdentity
import kotlinx.coroutines.future.await
import org.hibernate.reactive.pool.ReactiveConnection
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A runner over [jpa], for the migrations that take a [SqlMigrationSession].
 *
 * ```kotlin
 * SqlMigrations(jpa, listOf(V1Orders(), V2OrderIndex())).run()
 * ```
 *
 * **`poolSize` must be at least two, and this refuses to be built otherwise.** A run holds one
 * connection for the migration's own statements and needs a second for the lease watchdog renewing
 * the lock underneath it. On a pool of one that is a deadlock at startup: the watchdog waits for a
 * connection the migration will not give back until it finishes, the lease expires, and nothing says
 * anything. `JpaConfig` refuses `poolSize < 1` in the same spirit and for a much less subtle reason.
 *
 * The ledger and the runner share one identity, so `locked_by` and `applied_by` name the same run.
 */
@Suppress("ktlint:standard:function-naming")
fun SqlMigrations(
    jpa: Jpa,
    migrations: List<SqlMigration>,
    /** The record table. The lock lives in `<table>_lock`. */
    table: String = "stx_migrations",
    lease: Duration = 5.minutes,
    lockTimeout: Duration = 5.minutes,
    lockPoll: Duration = 1.seconds,
    staleAfter: Duration = 15.minutes,
    identity: String = migrationIdentity(),
): MigrationRunner<SqlMigrationSession> {
    require(jpa.config.poolSize >= 2) {
        "poolSize is ${jpa.config.poolSize}: a migration run holds one connection for the migration " +
            "and needs another for the lease watchdog, so a pool of one deadlocks at startup"
    }
    return MigrationRunner(
        ledger = SqlMigrationLedger(jpa, table, lease, identity),
        migrations = migrations,
        context = MigrationContext { block -> jpa.connection { block(BorrowedSession(it)) } },
        lockTimeout = lockTimeout,
        lockPoll = lockPoll,
        staleAfter = staleAfter,
        identity = identity,
    )
}

/**
 * A [SqlMigrationSession] over one borrowed connection, live only for the length of one migration.
 *
 * Two statements on one connection must not overlap — [ReactiveConnection]'s own rule — which a
 * migration awaiting each call in turn keeps, and one that opened an `async` per statement would not.
 */
private class BorrowedSession(
    private val connection: ReactiveConnection,
) : SqlMigrationSession {
    override suspend fun execute(sql: String) {
        connection.executeUnprepared(sql).await()
    }

    override suspend fun update(sql: String): Int = connection.update(sql).await()
}
