package com.softistx.spring.testing

import com.mongodb.reactivestreams.client.MongoClients
import com.softistx.testing.containers.TestNames
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.bson.Document

/**
 * That the run's database is actually taken away again.
 *
 * Asserted through [MongoTestCleanup.drop] rather than by letting the shutdown hook run, because a
 * hook fires after the last assertion has been reported — so what it does is otherwise visible only
 * to whoever thinks to look at the server afterwards. Nobody does, which is how a harness comes to
 * leave a database per run behind on the workspace's own replica set.
 */
class MongoTestCleanupTest :
    FeatureSpec({

        feature("the database a run made").config(enabled = mongoAvailable) {
            scenario("is gone once it is dropped, and it was there first") {
                val uri = TestMongo.service.requireEndpoint()
                val name = TestNames("stx_cleanup_test", separator = "_").next()

                MongoClients.create(uri).use { client ->
                    // A database exists once something is written to it, and not before.
                    client
                        .getDatabase(name)
                        .getCollection("anything")
                        .insertOne(Document("_id", "1"))
                        .awaitFirstOrNull()

                    client.listDatabaseNames().asFlow().toList() shouldContain name

                    MongoTestCleanup.drop(uri, name)

                    client.listDatabaseNames().asFlow().toList() shouldNotContain name
                }
            }
        }
    })
