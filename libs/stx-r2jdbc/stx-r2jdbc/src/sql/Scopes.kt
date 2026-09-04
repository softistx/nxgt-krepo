package com.softistx.r2jdbc.sql

import com.softistx.r2jdbc.R2jdbc
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext

/**
 * Runs [block] with one connection held for the whole of it, and gives it back afterwards.
 *
 * ```kotlin
 * db.connection { sql ->
 *     sql.unprepared("create temporary table batch (id bigint)")
 *     sql.execute("insert into batch values (?)", id)
 * }
 * ```
 *
 * **For statements that have to see each other**, which is a shorter list than it sounds: a
 * temporary table, a session `set`, MySQL's `last_insert_id()`. Everything else should go through
 * `db.sql`, which takes a connection per statement and hands it straight back — holding one for
 * longer than a statement is how a reactive pool comes to be sized like a blocking one.
 *
 * **There is no transaction here.** Each statement commits on its own, which is the honest default:
 * a scope that silently opened one would make `connection { }` and [transaction] the same thing with
 * different names.
 *
 * The connection is returned however [block] ends, under [NonCancellable] — a cancelled coroutine
 * that skipped the close would leak one out of a fixed-size pool, and enough of them hang the
 * application at the next statement.
 */
suspend fun <T> R2jdbc.connection(block: suspend (Sql) -> T): T {
    val connection = pool.connection.toCompletionStage().await()
    try {
        return block(Sql(connection, backend))
    } finally {
        withContext(NonCancellable) { connection.close().toCompletionStage().await() }
    }
}

/**
 * Runs [block] in a transaction on one connection, committing when it returns and rolling back when
 * it throws.
 *
 * ```kotlin
 * db.transaction { sql ->
 *     sql.execute("update accounts set balance = balance - ? where id = ?", amount, from)
 *     sql.execute("update accounts set balance = balance + ? where id = ?", amount, to)
 * }
 * ```
 *
 * **The transaction is the connection**, which is what makes this the whole of the mechanism.
 * `DriverContractTest` measures both halves on both servers: a row written and not yet committed is
 * invisible to every other connection in the pool, and the connection is reusable the moment the
 * transaction ends. So a `db.sql.query(…)` *inside* this block reads the database as it was before
 * the block started — it runs on a different connection — and that is the one sharp edge here.
 * Statements that belong to the transaction go through the `sql` this hands you.
 *
 * **It may suspend on something else in the middle.** Unlike `stx-jpa`, where a session belongs to
 * the thread that opened it and a naive coroutine bridge earns `HR000069`, the Vert.x client asserts
 * no thread ownership: `ThreadRoamingTest` suspends on a delay inside a transaction, resumes on
 * another thread, and commits. Two statements in flight *at once* on one connection are also legal,
 * and serialised by the driver rather than run in parallel — so an `async` per statement inside this
 * block buys nothing and is not an error.
 *
 * A rollback that itself fails does not replace the exception that caused it; the caller sees what
 * went wrong, not what went wrong while giving up.
 */
suspend fun <T> R2jdbc.transaction(block: suspend (Sql) -> T): T {
    val connection = pool.connection.toCompletionStage().await()
    try {
        val transaction = connection.begin().toCompletionStage().await()
        val result =
            try {
                block(Sql(connection, backend))
            } catch (failure: Throwable) {
                withContext(NonCancellable) { runCatching { transaction.rollback().toCompletionStage().await() } }
                throw failure
            }
        transaction.commit().toCompletionStage().await()
        return result
    } finally {
        withContext(NonCancellable) { connection.close().toCompletionStage().await() }
    }
}
