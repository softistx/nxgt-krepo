package com.strange.mongo.service

import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCluster
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.strange.mongo.DocumentNotFoundException
import com.strange.mongo.MongoTestCluster
import com.strange.mongo.Note
import com.strange.mongo.collection
import com.strange.mongo.repository.MongoCrudRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.firstOrNull
import org.bson.BsonDocument
import org.bson.conversions.Bson

/** A hook that fails after the document is written — the reason transactions are worth wiring. */
private class FailingTaskService(
    database: MongoDatabase,
    transactions: MongoCluster?,
) : TaskService(database, principal = "tester", transactions = transactions) {
    override suspend fun afterCreate(
        created: Task,
        session: ClientSession?,
    ) = error("the hook failed")
}

/** A service over an entity that does not implement `Audited`. */
private class NoteService(
    database: MongoDatabase,
) : MongoCrudService<Note, String, Note, String>(
        MongoCrudRepository(database.collection<Note>("notes"), Note::id),
        principal = "tester",
    ) {
    override suspend fun buildCreate(input: Note): Note = input

    override suspend fun buildUpdate(
        existing: Note,
        input: String,
    ): List<Bson> = listOf(Updates.set("text", input))
}

class MongoCrudServiceTest :
    FeatureSpec({

        feature("creating").config(enabled = MongoTestCluster.available) {
            scenario("the document is read back, and both hooks ran around it") {
                MongoTestCluster.withDatabase { _, database ->
                    val tasks = TaskService(database, principal = "tester")

                    val created = tasks.create(NewTask("t1", "write it down"))

                    created.title shouldBe "write it down"
                    created.metadata.createdBy shouldBe "tester"
                    created.metadata.lastModifiedBy shouldBe "tester"
                    tasks.calls shouldBe listOf("beforeCreate", "afterCreate")
                }
            }
        }

        feature("updating").config(enabled = MongoTestCluster.available) {
            scenario("it answers with the document as it now stands, stamped") {
                MongoTestCluster.withDatabase { _, database ->
                    val tasks = TaskService(database, principal = "tester")
                    val created = tasks.create(NewTask("t1", "write it down"))

                    val updated = tasks.update("t1", EditTask(done = true))

                    updated.done shouldBe true
                    updated.metadata.createdBy shouldBe "tester"
                    updated.metadata.lastModifiedAt shouldNotBe created.metadata.lastModifiedAt
                    tasks.calls shouldBe listOf("beforeCreate", "afterCreate", "beforeUpdate", "afterUpdate")
                }
            }

            scenario("an update that changes nothing writes nothing, and is not stamped") {
                MongoTestCluster.withDatabase { _, database ->
                    val tasks = TaskService(database, principal = "tester")
                    val created = tasks.create(NewTask("t1", "write it down"))

                    val untouched = tasks.update("t1", EditTask())

                    untouched shouldBe created
                    tasks.calls shouldBe listOf("beforeCreate", "afterCreate", "beforeUpdate")
                }
            }

            scenario("with nobody acting, nothing is stamped") {
                MongoTestCluster.withDatabase { _, database ->
                    val tasks = TaskService(database)
                    tasks.create(NewTask("t1", "write it down"))

                    tasks.update("t1", EditTask(done = true)).metadata.lastModifiedBy shouldBe ""
                }
            }

            scenario("a document that is not there is not an update") {
                MongoTestCluster.withDatabase { _, database ->
                    shouldThrow<DocumentNotFoundException> {
                        TaskService(database).update("missing", EditTask(done = true))
                    }
                }
            }
        }

        feature("deleting").config(enabled = MongoTestCluster.available) {
            scenario("by id, with the hooks around it") {
                MongoTestCluster.withDatabase { _, database ->
                    val tasks = TaskService(database)
                    tasks.create(NewTask("t1", "write it down"))

                    tasks.delete("t1")

                    tasks.findByIdOrNull("t1") shouldBe null
                    tasks.calls shouldBe listOf("beforeCreate", "afterCreate", "beforeDelete", "afterDelete")
                }
            }

            scenario("deleting one that is not there says so; deleting none of many does not") {
                MongoTestCluster.withDatabase { _, database ->
                    val tasks = TaskService(database)

                    shouldThrow<DocumentNotFoundException> { tasks.delete("missing") }
                    tasks.deleteAll(listOf("missing")) shouldBe 0
                }
            }
        }

        feature("a hook that fails after the write").config(enabled = MongoTestCluster.available) {
            scenario("with a cluster to open a transaction on, the document is rolled back") {
                MongoTestCluster.withDatabase { client, database ->
                    val tasks = FailingTaskService(database, transactions = client)

                    shouldThrow<IllegalStateException> { tasks.create(NewTask("t1", "write it down")) }

                    TaskService(database).findByIdOrNull("t1") shouldBe null
                }
            }

            scenario("without one, the write stands — which is the cost of leaving it out") {
                MongoTestCluster.withDatabase { _, database ->
                    val tasks = FailingTaskService(database, transactions = null)

                    shouldThrow<IllegalStateException> { tasks.create(NewTask("t1", "write it down")) }

                    TaskService(database).findByIdOrNull("t1") shouldNotBe null
                }
            }
        }

        feature("an entity that is not audited").config(enabled = MongoTestCluster.available) {
            scenario("no metadata appears in a collection that never asked for one") {
                MongoTestCluster.withDatabase { _, database ->
                    val notes = NoteService(database)
                    notes.create(Note("n1", "one"))

                    notes.update("n1", "edited").text shouldBe "edited"

                    val stored =
                        database
                            .collection<BsonDocument>("notes")
                            .find(BsonDocument())
                            .firstOrNull()
                    stored?.containsKey("metadata") shouldBe false
                }
            }
        }
    })
