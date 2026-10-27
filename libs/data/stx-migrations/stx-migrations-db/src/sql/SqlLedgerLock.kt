package com.softistx.migrations.db.sql

import com.softistx.common.coroutines.Lease
import com.softistx.jpa.Jpa
import com.softistx.jpa.session.connection
import kotlinx.coroutines.future.await
import kotlin.time.Clock
import kotlin.time.Duration

/** The one row in the lock table. `varchar(1)` is the narrowest primary key both servers agree on. */
internal const val LOCK = "x"

/**
 * The lock row of [SqlMigrationLedger]: taken, renewed and released with one conditional `update`
 * each, on a connection borrowed from [jpa]'s pool.
 */
internal class SqlLedgerLock(
    private val jpa: Jpa,
    private val dialect: SqlDialect,
    private val locks: String,
    private val lease: Duration,
    private val owner: String,
) {
    private val guard = Lease(lease, ::take, ::renew, ::release)

    suspend fun <T> guard(block: suspend () -> T): T? = guard.guard(LOCK, block)

    private suspend fun take(id: String): Boolean {
        val now = Clock.System.now()
        return jpa.connection { connection ->
            connection
                .update(
                    "update $locks set locked_by = ${dialect.parameter(1)}, locked_until = ${dialect.parameter(2)} " +
                        "where id = ${dialect.parameter(3)} " +
                        "and (locked_until is null or locked_until < ${dialect.parameter(4)})",
                    arrayOf<Any?>(owner, (now + lease).toEpochMilliseconds(), id, now.toEpochMilliseconds()),
                ).await() == 1
        }
    }

    private suspend fun renew(id: String): Boolean =
        jpa.connection { connection ->
            connection
                .update(
                    "update $locks set locked_until = ${dialect.parameter(1)} " +
                        "where id = ${dialect.parameter(2)} and locked_by = ${dialect.parameter(3)}",
                    arrayOf<Any?>((Clock.System.now() + lease).toEpochMilliseconds(), id, owner),
                ).await() == 1
        }

    private suspend fun release(id: String) {
        jpa.connection { connection ->
            connection
                .update(
                    "update $locks set locked_by = null, locked_until = null " +
                        "where id = ${dialect.parameter(1)} and locked_by = ${dialect.parameter(2)}",
                    arrayOf<Any?>(id, owner),
                ).await()
        }
    }

    suspend fun exists(): Boolean =
        jpa.connection { connection ->
            connection
                .select("select id from $locks where id = ${dialect.parameter(1)}", arrayOf<Any?>(LOCK))
                .await()
                .hasNext()
        }
}
