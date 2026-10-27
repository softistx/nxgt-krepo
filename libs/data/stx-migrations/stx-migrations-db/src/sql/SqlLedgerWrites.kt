package com.softistx.migrations.db.sql

import com.softistx.jpa.Jpa
import com.softistx.jpa.session.connection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await

/**
 * Issues a `create table if not exists`, and tries once more if it lost a race.
 *
 * **`create table if not exists` is not atomic on PostgreSQL.** Two sessions issuing the same one
 * at the same moment can both pass its existence check, and the loser fails with
 * `duplicate key value violates unique constraint "pg_type_pkey"` rather than with the no-op the
 * clause promises. [SqlMigrationLedger.prepare] runs *before* the migration lock is taken — it has to, the lock
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
internal suspend fun Jpa.createTable(sql: String) {
    try {
        connection { it.executeUnprepared(sql).await() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        connection { it.executeUnprepared(sql).await() }
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
internal suspend fun Jpa.insertIfAbsent(
    sql: String,
    parameters: Array<Any?>,
    exists: suspend () -> Boolean,
): Boolean =
    try {
        connection { it.update(sql, parameters).await() }
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        if (exists()) false else throw failure
    }
