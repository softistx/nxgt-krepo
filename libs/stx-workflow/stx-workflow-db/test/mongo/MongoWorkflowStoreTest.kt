package com.strange.workflow.mongo

import com.strange.mongo.collection
import com.strange.mongo.query.findById
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.db.record
import com.strange.workflow.db.storeContract
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.toList
import org.bson.Document
import kotlin.time.Duration.Companion.minutes

class MongoWorkflowStoreTest :
    FeatureSpec({
        storeContract("MongoDB", MongoTestCluster.available) { lease, block ->
            MongoTestCluster.withDatabase { database -> block(MongoWorkflowStore(database, lease = lease)) }
        }

        feature("what only the Mongo store does").config(enabled = MongoTestCluster.available) {
            scenario("a finished instance is given an expiry, and one waiting for a person is not") {
                MongoTestCluster.withDatabase { database ->
                    val store = MongoWorkflowStore(database, retention = 30.minutes)
                    store.create(record("done"))
                    store.create(record("stuck"))

                    val done = store.load("done")!!
                    store.save(done.copy(status = WorkflowStatus.Completed), done.version) shouldBe true
                    val stuck = store.load("stuck")!!
                    store.save(stuck.copy(status = WorkflowStatus.Failed), stuck.version) shouldBe true

                    val instances = database.collection<WorkflowInstanceDocument>(WORKFLOW_INSTANCES)
                    instances.findById("done")!!.expiresAt shouldNotBe null
                    // Exempt for the same reason it gets no TTL on Redis and no finished_at on
                    // Postgres: it is waiting for a person, and expiring it deletes the only
                    // description of what has to be fixed.
                    instances.findById("stuck")!!.expiresAt shouldBe null
                }
            }

            scenario("the indexes the store needs are created before it is handed back") {
                MongoTestCluster.withDatabase { database ->
                    MongoWorkflowStore(database)

                    val indexed =
                        database
                            .collection<WorkflowInstanceDocument>(WORKFLOW_INSTANCES)
                            .listIndexes()
                            .toList()
                    // Named rather than counted: a count says nothing about which index is missing,
                    // and it fails for the wrong reason the moment a fourth one is added.
                    // runnable() is a ranged read over dueAt, find() is a filter on status ordered
                    // by updatedAt, and retention is the server expiring expiresAt. Each is a
                    // collection scan without its index — the kind of thing that works in a spec and
                    // does not work in a year.
                    val keys = indexed.mapNotNull { it.get("key", Document::class.java)?.keys?.toList() }
                    keys shouldContainExactlyInAnyOrder
                        listOf(
                            listOf("_id"),
                            listOf("dueAt"),
                            listOf("expiresAt"),
                            listOf("status", "updatedAt"),
                        )
                    // The server stores it as an int32, so read it as a Number rather than guessing.
                    indexed
                        .single { it.containsKey("expireAfterSeconds") }
                        .get("expireAfterSeconds", Number::class.java)
                        .toLong() shouldBe 0L
                }
            }
        }
    })
