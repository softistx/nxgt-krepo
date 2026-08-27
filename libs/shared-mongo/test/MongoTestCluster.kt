package com.strange.mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mongodb.reactivestreams.client.MongoClients
import com.strange.mongo.codec.mongoCodecRegistry
import kotlinx.coroutines.runBlocking
import org.bson.BsonDocument
import org.bson.BsonInt32
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import com.mongodb.reactivestreams.client.MongoClient as ReactiveMongoClient

/**
 * The MongoDB the integration tests talk to: the replica set this workspace already runs, not one
 * a test starts. `~/workspace/docker/apps/database/mongo` publishes it on the default port, and
 * `MONGO_TEST_URI` points the tests somewhere else when needed.
 *
 * A replica set is not a preference here — `startTransaction` fails outright against a standalone
 * `mongod`, so anything touching [withTransaction] needs one.
 *
 * [available] is what every such spec is enabled on, so a machine without the server reports those
 * tests as skipped rather than failing a build over something that is not the code.
 */
internal object MongoTestCluster {
    private val uri = System.getenv("MONGO_TEST_URI") ?: "mongodb://localhost:27017"

    private val databases = AtomicInteger()

    /** Short server selection, or an absent server would cost 30s per spec before failing. */
    private fun settings(): MongoClientSettings =
        MongoClientSettings
            .builder()
            .applyConnectionString(ConnectionString(uri))
            .applyToClusterSettings { it.serverSelectionTimeout(2, TimeUnit.SECONDS) }
            .codecRegistry(mongoCodecRegistry())
            .build()

    val available: Boolean by lazy {
        runCatching {
            MongoClient.create(settings()).use { client ->
                runBlocking { client.getDatabase("admin").runCommand<BsonDocument>(BsonDocument("hello", BsonInt32(1))) }
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
