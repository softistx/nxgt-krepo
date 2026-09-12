package com.softistx.migrations.db.sql

import com.softistx.common.coroutines.Lease
import com.softistx.jpa.Jpa
import com.softistx.jpa.session.connection
import com.softistx.migrations.MigrationRecord
import com.softistx.migrations.MigrationStatus
import com.softistx.migrations.ledger.MigrationLedger
import com.softistx.migrations.migrationIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import org.hibernate.reactive.pool.ReactiveConnection
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The one row in the lock table. `varchar(1)` is the narrowest primary key both servers agree on. */
private const val LOCK = "x"

/**
 * The ledger in a relational database: one row per version, and one more table holding the lock.
 *
 * **It goes through `stx-jpa`'s pool and never through JDBC**, because nothing in this repository
 * does. Every statement here is issued on a connection borrowed from the pool Hibernate is already
 * using — `jpa.connection { }`, the seam added for exactly this — and given straight back. DDL goes
 * out unprepared, which is the only way it goes out at all; the reads and the conditional writes are
 * prepared and bound.
 *
 * **Two tables, not one.** A lock is not a migration: a sentinel row in the ledger would surface in
 * [all] as a version that has run, and a `version = -1` to hide it is a convention every reader has
 * to be told about.
 *
 * **The tables are qualified with [JpaConfig.schema][com.softistx.jpa.JpaConfig.schema] when there is
 * one.** `NativeDdlTest` in `stx-jpa` measures why: Hibernate applies `hibernate.default_schema` when
 * it renders a statement from the mapping, and nothing renders these — an unqualified name lands in
 * the connection's own `search_path`, which on a schema-per-tenant deployment is not where the
 * application lives. **A migration's own statements are not qualified for it**, and it should say
 * where its tables go.
 *
 * **The instants are stored as epoch milliseconds.** The one place this ledger disagrees with the
 * MongoDB one, which writes BSON dates — and it disagrees because Mongo has a single unambiguous date
 * type and the two SQL servers do not: MySQL's `timestamp` converts to UTC on the way in and back to
 * the session's zone on the way out, `datetime` does not, and Postgres has no `datetime` at all. A
 * `bigint` means one thing everywhere. `to_timestamp(started_at / 1000)` is the reading glasses.
 *
 * It closes nothing: the [Jpa] belongs to the application that built it.
 */
class SqlMigrationLedger(
    private val jpa: Jpa,
    table: String = "stx_migrations",
    /** How long the lock is held before it is considered abandoned. Renewed at a third of it. */
    private val lease: Duration = 5.minutes,
    /** Written into `locked_by`, and into every row's `applied_by` by the runner. */
    owner: String = migrationIdentity(),
) : MigrationLedger {
    private val owner = owner.take(OWNER_LIMIT)
    private val dialect = SqlDialect.of(jpa.config.uri)
    private val ledger = qualify(table)
    private val locks = qualify("${table}_lock")

    private val guard = Lease(lease, ::take, ::renew, ::release)

    private fun qualify(name: String) = jpa.config.schema?.let { "$it.$name" } ?: name

    private fun parameters(count: Int) = (1..count).joinToString(", ") { dialect.parameter(it) }

    /**
     * Both tables and the lock row, none of which fails when it is already there.
     *
     * `create table if not exists` is Postgres and MySQL; [SqlDialect] is where a third server with
     * no such form is refused. The `varchar` lengths are bounds rather than guesses at content, and
     * every value is truncated to its bound on the way in — a failure message that was too long must
     * not be what fails the write that is recording a failure.
     */
    override suspend fun prepare() {
        createTable(
            """
            create table if not exists $ledger (
                version bigint primary key,
                description varchar(500) not null,
                status varchar(16) not null,
                applied_by varchar(200),
                failure varchar(2000),
                duration_ms bigint,
                started_at bigint not null,
                updated_at bigint not null
            )
            """.trimIndent(),
        )
        createTable(
            """
            create table if not exists $locks (
                id varchar(1) primary key,
                locked_by varchar(200),
                locked_until bigint
            )
            """.trimIndent(),
        )
        insertIfAbsent(
            "insert into $locks (id, locked_by, locked_until) values (${parameters(1)}, null, null)",
            arrayOf<Any?>(LOCK),
        ) { lockExists() }
    }

    override suspend fun find(version: Long): MigrationRecord? =
        jpa.connection { connection ->
            connection.rows("$SELECT from $ledger where version = ${dialect.parameter(1)}", version).firstOrNull()
        }

    /**
     * One insert, and the primary key is the refusal — see [insertIfAbsent] for how it is read.
     *
     * `insert … where not exists` would have answered the same question in one round trip and with no
     * uniqueness behind it, which is a race rather than a claim.
     */
    override suspend fun claim(record: MigrationRecord): Boolean =
        insertIfAbsent(
            "insert into $ledger " +
                "(version, description, status, applied_by, failure, duration_ms, started_at, updated_at) " +
                "values (${parameters(8)})",
            record.toRow(),
        ) { find(record.version) != null }

    override suspend fun update(record: MigrationRecord) {
        jpa.connection { connection ->
            connection
                .update(
                    "update $ledger set description = ${dialect.parameter(1)}, status = ${dialect.parameter(2)}, " +
                        "applied_by = ${dialect.parameter(3)}, failure = ${dialect.parameter(4)}, " +
                        "duration_ms = ${dialect.parameter(5)}, updated_at = ${dialect.parameter(6)} " +
                        "where version = ${dialect.parameter(7)}",
                    arrayOf<Any?>(
                        record.description,
                        record.status.name,
                        record.appliedBy?.take(OWNER_LIMIT),
                        record.failure?.take(FAILURE_LIMIT),
                        record.durationMillis,
                        record.updatedAt.toEpochMilliseconds(),
                        record.version,
                    ),
                ).await()
        }
    }

    override suspend fun blocking(staleAfter: Duration): MigrationRecord? {
        val cutoff = (Clock.System.now() - staleAfter).toEpochMilliseconds()
        return jpa.connection { connection ->
            connection
                .rows(
                    "$SELECT from $ledger where status = ${dialect.parameter(1)} " +
                        "or (status = ${dialect.parameter(2)} and updated_at < ${dialect.parameter(3)}) " +
                        "order by version asc",
                    MigrationStatus.FAILED.name,
                    MigrationStatus.RUNNING.name,
                    cutoff,
                ).firstOrNull()
        }
    }

    override suspend fun all(): List<MigrationRecord> =
        jpa.connection { connection -> connection.rows("$SELECT from $ledger order by version asc") }

    /**
     * One conditional `update` on a one-row table, three times over.
     *
     * The affected-row count is the answer, which is the same shape `JpaWorkflowStore` uses and for
     * the reason its KDoc gives: a `select … for update` row lock lives inside a transaction, and
     * this lock has to outlive every statement taken under it.
     */
    override suspend fun <T> guarded(block: suspend () -> T): T? = guard.guard(LOCK, block)

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

    /**
     * Issues a `create table if not exists`, and tries once more if it lost a race.
     *
     * **`create table if not exists` is not atomic on PostgreSQL.** Two sessions issuing the same one
     * at the same moment can both pass its existence check, and the loser fails with
     * `duplicate key value violates unique constraint "pg_type_pkey"` rather than with the no-op the
     * clause promises. [prepare] runs *before* the migration lock is taken — it has to, the lock
     * table is one of the two things it creates — so a rolling deploy starting several instances
     * together is exactly that moment, and nothing else is holding them apart.
     *
     * The retry is the whole fix: by then the winner has committed, the existence check sees the
     * table and the statement does nothing. A second failure is a real one and is thrown untouched,
     * which is what a bad schema name or a missing grant reports as.
     *
     * Retried rather than serialised behind an advisory lock, for the reason `JpaWorkflowStore` gives
     * about row locks — a lock spelled differently on every server is one this library would have to
     * know every server to take, and [SqlDialect] exists to stay as small as it is.
     */
    private suspend fun createTable(sql: String) {
        try {
            jpa.connection { it.executeUnprepared(sql).await() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            jpa.connection { it.executeUnprepared(sql).await() }
        }
    }

    /**
     * Runs an insert, and answers whether **this** call is the one that wrote the row.
     *
     * The insert is plain: no `on conflict do nothing`, no `insert ignore`. Both were tried and
     * neither can answer the question portably — Postgres reports zero affected rows for a conflict,
     * and MySQL reports one, because the Vert.x client sets `CLIENT_FOUND_ROWS` and a matched row
     * counts as affected. So the failure is caught and the database is asked: if [exists] now says
     * the row is there, this caller lost a race and answers false; anything else is a real write
     * failure and is rethrown untouched.
     *
     * A read on a path that should never be taken — [claim] is only ever called under the migration
     * lock — in exchange for being right on any server rather than on the two [SqlDialect] could name.
     */
    private suspend fun insertIfAbsent(
        sql: String,
        parameters: Array<Any?>,
        exists: suspend () -> Boolean,
    ): Boolean =
        try {
            jpa.connection { it.update(sql, parameters).await() }
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            if (exists()) false else throw failure
        }

    private suspend fun lockExists(): Boolean =
        jpa.connection { connection ->
            connection
                .select("select id from $locks where id = ${dialect.parameter(1)}", arrayOf<Any?>(LOCK))
                .await()
                .hasNext()
        }

    private suspend fun ReactiveConnection.rows(
        sql: String,
        vararg parameters: Any?,
    ): List<MigrationRecord> {
        val result = if (parameters.isEmpty()) select(sql).await() else select(sql, arrayOf(*parameters)).await()
        return buildList { while (result.hasNext()) add(result.next().toRecord()) }
    }

    private fun MigrationRecord.toRow(): Array<Any?> =
        arrayOf<Any?>(
            version,
            description.take(DESCRIPTION_LIMIT),
            status.name,
            appliedBy?.take(OWNER_LIMIT),
            failure?.take(FAILURE_LIMIT),
            durationMillis,
            at.toEpochMilliseconds(),
            updatedAt.toEpochMilliseconds(),
        )

    private fun Array<out Any?>.toRecord(): MigrationRecord =
        MigrationRecord(
            version = this[0] as Long,
            description = this[1] as String,
            status = MigrationStatus.valueOf(this[2] as String),
            at = Instant.fromEpochMilliseconds(this[6] as Long),
            updatedAt = Instant.fromEpochMilliseconds(this[7] as Long),
            appliedBy = this[3] as String?,
            failure = this[4] as String?,
            durationMillis = this[5] as Long?,
        )

    private companion object {
        /** The column list, written once so [toRecord]'s positions cannot drift from it. */
        const val SELECT =
            "select version, description, status, applied_by, failure, duration_ms, started_at, updated_at"

        const val DESCRIPTION_LIMIT = 500
        const val FAILURE_LIMIT = 2000
        const val OWNER_LIMIT = 200
    }
}
