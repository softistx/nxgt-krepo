package com.strange.mongo

import com.mongodb.client.model.Filters
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.count
import kotlinx.serialization.Serializable
import org.bson.BsonDocument

@Serializable
private data class Note(
    val _id: String,
    val text: String,
)

/**
 * Rollback is the half that is easy to get wrong and impossible to prove without a server, so these
 * run against the replica set this workspace already has up. What they pin down is the contract in
 * the KDoc: the failure the caller sees is the one they threw, and nothing the block wrote survives
 * it.
 */
class TransactionsTest :
    FeatureSpec({

        feature("a transaction that returns").config(enabled = MongoTestCluster.available) {
            scenario("its writes are committed and its value is handed back") {
                MongoTestCluster.withDatabase { client, database ->
                    val notes = database.collection<Note>("notes")

                    val id =
                        client.withTransaction { session ->
                            notes.insertOne(session, Note("n1", "kept"))
                            "n1"
                        }

                    id shouldBe "n1"
                    notes.find(Filters.eq("_id", "n1")).count() shouldBe 1
                }
            }
        }

        feature("a transaction whose block throws").config(enabled = MongoTestCluster.available) {
            scenario("the exception is the caller's own, and the writes are gone") {
                MongoTestCluster.withDatabase { client, database ->
                    val notes = database.collection<Note>("notes")

                    shouldThrow<IllegalStateException> {
                        client.withTransaction { session ->
                            notes.insertOne(session, Note("n2", "rolled back"))
                            error("no")
                        }
                    }

                    notes.find(BsonDocument()).count() shouldBe 0
                }
            }
        }

        feature("the database-scoped overload").config(enabled = MongoTestCluster.available) {
            scenario("it resolves the database on the same cluster and shares the session") {
                MongoTestCluster.withDatabase { client, database ->
                    client.withTransaction(database.name) { scoped, session ->
                        scoped.collection<Note>("notes").insertOne(session, Note("n3", "scoped"))
                    }

                    database.collection<Note>("notes").find(BsonDocument()).count() shouldBe 1
                }
            }
        }
    })
