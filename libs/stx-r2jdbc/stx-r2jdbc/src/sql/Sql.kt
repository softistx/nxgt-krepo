package com.softistx.r2jdbc.sql

import com.softistx.r2jdbc.Backend
import com.softistx.r2jdbc.R2jdbcException
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.future.await
import org.intellij.lang.annotations.Language

/**
 * Somewhere to run a statement: the pool, a connection pinned by `connection { }`, or the connection
 * inside `transaction { }`.
 *
 * One type for all three because the driver has one — `Pool` and `SqlConnection` are both an
 * `SqlClient` — and because what differs between them is *where* a statement runs, not what a caller
 * may write. Which one is in hand is decided by the scope the caller opened; nothing here needs to
 * know which it got.
 *
 * **Parameters are bound, never interpolated.** Every method takes the values separately and hands
 * them to the driver as a `Tuple`, so a `'` in a value is a `'` and not a second statement. The
 * placeholder is `?` on both servers — see [numberedPlaceholders] for what becomes of it on
 * Postgres, and why a caller does not write `$1`.
 */
class Sql internal constructor(
    private val client: SqlClient,
    private val backend: Backend,
) {
    /**
     * Runs [sql] and returns every row it produced.
     *
     * The rows are materialised. A driver `RowSet` already holds them all in memory, so a `List` is
     * an honest description of what has happened rather than a stream this library could pretend to
     * offer — and a query whose result does not fit in memory wants a `limit`, not a lazier type.
     */
    suspend fun query(
        @Language("SQL") sql: String,
        vararg parameters: Any?,
    ): List<Row> = prepared(sql, parameters).toList()

    /**
     * The one row [sql] produced, or null when it produced none.
     *
     * More than one row is a programming error and says so, rather than quietly returning the first:
     * a `select … where id = ?` that matches twice has found a broken key, and returning row one is
     * how it stays broken.
     */
    suspend fun queryOne(
        @Language("SQL") sql: String,
        vararg parameters: Any?,
    ): Row? {
        val rows = prepared(sql, parameters)
        if (rows.size() > 1) throw R2jdbcException("queryOne matched ${rows.size()} rows: $sql")
        return rows.firstOrNull()
    }

    /**
     * Runs [sql] and returns how many rows it *matched*.
     *
     * Matched, not changed — the same answer on both servers, and worth saying because the two are
     * usually reported to differ. An `update` writing a row's existing value back reports `1` on
     * Postgres and on MySQL alike; `DriverContractTest` measures it. So this counts what the `where`
     * found, and a caller testing it for zero is asking *did the row exist*, not *did anything
     * change*.
     */
    suspend fun execute(
        @Language("SQL") sql: String,
        vararg parameters: Any?,
    ): Int = prepared(sql, parameters).rowCount()

    /**
     * Sends [sql] as written, with no parameters and no preparation, and returns the rows it made.
     *
     * **The escape, and the only method here that does not bind anything.** The extended protocol a
     * prepared statement uses carries one statement, so a body with two `;` in it, and some DDL,
     * cannot go through the four methods above — `stx-jpa` hit the same wall from the other side and
     * documents `jpa.connection { }` as its way out. This is the same door, and it is narrower on
     * purpose: no parameters means nothing to interpolate, so a value from outside the program has
     * no way in.
     *
     * Postgres also spells three `jsonb` operators with a `?`, which a prepared statement here would
     * renumber. Either write `??`, or send the statement through this.
     */
    suspend fun unprepared(
        @Language("SQL") sql: String,
    ): List<Row> =
        client
            .query(sql)
            .execute()
            .toCompletionStage()
            .await()
            .toList()

    private suspend fun prepared(
        sql: String,
        parameters: Array<out Any?>,
    ) = client
        .preparedQuery(sql.numberedPlaceholders(backend))
        .execute(Tuple.tuple(parameters.toList()))
        .toCompletionStage()
        .await()
}
