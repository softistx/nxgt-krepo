package com.softistx.telemetry.mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.testing.containers.TestNames
import com.softistx.testing.containers.mongoContainer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * The MongoDB the integration specs write to: a container for the run, unless `MONGO_TEST_URI` names
 * a server that is already up.
 *
 * A standalone `mongod` is enough here — nothing in this module opens a transaction — so this is the
 * cheaper of the two harnesses `stx-mongo` needs. What it shares with that one is the order: the
 * container is the default, because a suite that only passes on a machine with the right daemons
 * running passes for the wrong reason.
 */
internal object MongoTestServer {
    private val mongo = mongoContainer()

    /** A database per spec, and one no other run will pick — see [TestNames]. */
    private val databases = TestNames("stx_telemetry_test", separator = "_")

    /** Where the server is, for a spec that opens a client of its own rather than borrowing one. */
    val uri: String get() = mongo.requireEndpoint()

    /** The next unused database name, for a spec that has to drop it itself with [drop]. */
    fun next(): String = databases.next()

    /** Drops a database [next] handed out. The server outlives the run when it is the workspace's. */
    suspend fun drop(name: String) = MongoClient.create(settings()).use { it.getDatabase(name).drop() }

    /** Short server selection, or an absent server would cost 30s per spec before failing. */
    private fun settings(): MongoClientSettings =
        MongoClientSettings
            .builder()
            .applyConnectionString(ConnectionString(mongo.requireEndpoint()))
            .applyToClusterSettings { it.serverSelectionTimeout(2, TimeUnit.SECONDS) }
            .build()

    /** Reachable *and* answering: starting a container proves only that a process came up. */
    val available: Boolean by lazy {
        mongo.available &&
            runCatching {
                MongoClient.create(settings()).use { client ->
                    runBlocking { client.listDatabaseNames().firstOrNull() }
                }
            }.isSuccess
    }

    /**
     * Runs [block] against a database no other test is using, and drops it afterwards.
     *
     * The server is shared and long-lived when `MONGO_TEST_URI` points at the workspace's own, so a
     * spec that left its documents behind would pollute somebody else's afternoon.
     */
    suspend fun <T> withDatabase(block: suspend (MongoDatabase) -> T): T =
        MongoClient.create(settings()).use { client ->
            val database = client.getDatabase(databases.next())
            try {
                block(database)
            } finally {
                database.drop()
            }
        }
}
