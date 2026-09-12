package com.softistx.workflow.mongo

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.mongo.mongoClient
import com.softistx.testing.containers.TestNames
import com.softistx.testing.containers.mongoContainer
import kotlinx.coroutines.runBlocking
import org.bson.BsonDocument
import kotlin.time.Duration.Companion.seconds

/**
 * The MongoDB the Mongo specs talk to: the workspace's own when `MONGO_TEST_URI` names it, a
 * single-node replica set started for the run otherwise.
 *
 * A database per spec, dropped afterwards — `stx-mongo`'s habit, for its reason: two specs that
 * shared one would see each other's instances, and a run pointed at a real server would be writing
 * into somebody's data.
 */
internal object MongoTestCluster {
    private val mongo = mongoContainer()
    private val databases = TestNames("stx-workflow-test")

    val available: Boolean by lazy {
        mongo.available &&
            runCatching {
                runBlocking {
                    client().use { it.getDatabase("admin").runCommand(BsonDocument("hello", org.bson.BsonInt32(1))) }
                }
            }.isSuccess
    }

    private fun client(): MongoClient =
        mongoClient(mongo.requireEndpoint()) {
            // Fail fast rather than spending the driver's 30-second default deciding a server that
            // is not there is not there.
            applyToClusterSettings { it.serverSelectionTimeout(2.seconds.inWholeMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS) }
        }

    suspend fun withDatabase(block: suspend (MongoDatabase) -> Unit) {
        client().use { client ->
            val database = client.getDatabase(databases.next())
            try {
                block(database)
            } finally {
                database.drop()
            }
        }
    }
}
