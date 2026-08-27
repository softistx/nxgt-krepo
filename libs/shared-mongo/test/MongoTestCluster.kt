package com.strange.mongo

import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mongodb.reactivestreams.client.MongoClients
import com.strange.testing.containers.mongoContainer
import kotlinx.coroutines.runBlocking
import org.bson.BsonDocument
import org.bson.BsonInt32
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import com.mongodb.reactivestreams.client.MongoClient as ReactiveMongoClient

/**
 * The MongoDB the integration tests talk to: a replica set started for this run, unless
 * `MONGO_TEST_URI` names one that is already up.
 *
 * A replica set is not a preference here — `startTransaction` fails outright against a standalone
 * `mongod`, so anything touching [withTransaction] needs one. `MongoDBContainer` initiates one; so
 * does `~/workspace/docker/apps/database/mongo`, which is what the override points at on a machine
 * where it is already running.
 *
 * The default is the container, and that is the change worth naming: a suite that only passes on a
 * machine with the right daemons already up passes for the wrong reason, and says nothing to anyone
 * who checks the repo out tomorrow.
 *
 * [available] is what every integration spec is enabled on, so a machine with neither Docker nor a
 * server reports those tests as skipped rather than failing a build over something that is not the
 * code.
 */
internal object MongoTestCluster {
    private val mongo = mongoContainer()

    private val databases = AtomicInteger()

    /** Where the cluster is, for a spec that builds its own client rather than borrowing this one. */
    val uri: String get() = requireNotNull(mongo.endpoint) { mongo.describe() }

    /** Short server selection, or an absent server would cost 30s per spec before failing. */
    private fun settings(): MongoClientSettings =
        mongoClientSettings(uri) {
            applyToClusterSettings { it.serverSelectionTimeout(2, TimeUnit.SECONDS) }
        }

    /**
     * Reachable *and* answering. Starting a container proves the process is up; a `hello` proves the
     * replica set finished initiating, which is the part transactions actually need.
     */
    val available: Boolean by lazy {
        mongo.available &&
            runCatching {
                MongoClient.create(settings()).use { client ->
                    runBlocking {
                        client.getDatabase("admin").runCommand<BsonDocument>(BsonDocument("hello", BsonInt32(1)))
                    }
                }
            }.isSuccess
    }

    fun client(): MongoClient = MongoClient.create(settings())

    /**
     * The Reactive Streams client GridFS needs. Built from the same settings as [client], which is
     * also how an application should do it — one pool behind both APIs.
     */
    fun reactiveClient(): ReactiveMongoClient = MongoClients.create(settings())

    /**
     * Runs [block] against a database no other test is using, and drops it afterwards — the server
     * is shared and long-lived, so a test that leaves data behind pollutes someone else's work.
     */
    suspend fun <T> withDatabase(block: suspend (MongoClient, MongoDatabase) -> T): T =
        client().use { client ->
            val database = client.getDatabase("shared-mongo-test-${databases.incrementAndGet()}")
            try {
                block(client, database)
            } finally {
                database.drop()
            }
        }
}
