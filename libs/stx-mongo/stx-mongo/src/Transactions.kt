package com.softistx.mongo

import com.mongodb.TransactionOptions
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCluster
import com.mongodb.kotlin.client.coroutine.MongoDatabase

/**
 * Runs [block] in a transaction, committing when it returns and aborting when it throws.
 *
 * The synchronous driver has `ClientSession.withTransaction`; the coroutine one does not, because
 * the callback would have to be suspending. This is that method, minus the retry loop: a
 * `TransientTransactionError` propagates rather than being replayed, since replaying a suspending
 * block whose side effects the driver cannot see is not a library's call to make.
 *
 * The session is closed either way, and aborting is guarded by `hasActiveTransaction` so that a
 * failure *inside* `commitTransaction` is reported as itself instead of being masked by the
 * `no transaction started` that a blind abort would raise.
 *
 * Transactions need a replica set or a sharded cluster; against a standalone `mongod` the driver
 * fails the moment the transaction starts.
 */
suspend fun <T> MongoCluster.withTransaction(
    options: TransactionOptions? = null,
    block: suspend (ClientSession) -> T,
): T {
    val session = startSession()
    try {
        if (options == null) session.startTransaction() else session.startTransaction(options)
        val result =
            try {
                block(session)
            } catch (e: Throwable) {
                if (session.hasActiveTransaction()) session.abortTransaction()
                throw e
            }
        session.commitTransaction()
        return result
    } finally {
        session.close()
    }
}

/**
 * [withTransaction] for the common case of one database — [block] gets that database resolved on
 * this cluster, so a caller does not have to keep both handles in scope.
 */
suspend fun <T> MongoCluster.withTransaction(
    databaseName: String,
    options: TransactionOptions? = null,
    block: suspend (MongoDatabase, ClientSession) -> T,
): T = withTransaction(options) { session -> block(getDatabase(databaseName), session) }
