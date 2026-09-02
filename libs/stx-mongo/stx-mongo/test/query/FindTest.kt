package com.softistx.mongo.query

import com.mongodb.client.model.Filters
import com.softistx.mongo.DocumentNotFoundException
import com.softistx.mongo.MongoTestCluster
import com.softistx.mongo.Note
import com.softistx.mongo.withNotes
import com.softistx.mongo.withNotesAndClient
import com.softistx.mongo.withTransaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList

/**
 * Reads, against a real server — a query helper that compiles proves nothing about what the server
 * was asked.
 */
class FindTest :
    FeatureSpec({

        feature("reading without a session").config(enabled = MongoTestCluster.available) {
            scenario("findAll with no filter is the whole collection") {
                withNotes { notes ->
                    notes.insertAll(listOf(Note("n1", "one"), Note("n2", "two")))

                    notes.findAll().toList().map { it.id } shouldContainExactlyInAnyOrder listOf("n1", "n2")
                }
            }

            scenario("findOne takes the first match and no more") {
                withNotes { notes ->
                    notes.insertAll(listOf(Note("n1", "one"), Note("n2", "one")))

                    notes.findOne(Filters.eq("text", "one"))?.text shouldBe "one"
                    notes.findOne(Filters.eq("text", "absent")) shouldBe null
                }
            }

            scenario("findById maps the entity's id onto _id") {
                withNotes { notes ->
                    notes.insert(Note("n1", "one"))

                    notes.findById("n1")?.text shouldBe "one"
                    notes.findById("missing") shouldBe null
                }
            }

            scenario("findByIds returns the ones that are there") {
                withNotes { notes ->
                    notes.insertAll(listOf(Note("n1", "one"), Note("n2", "two")))

                    notes.findByIds(listOf("n1", "missing")).toList().map { it.id } shouldBe listOf("n1")
                }
            }
        }

        feature("requireById").config(enabled = MongoTestCluster.available) {
            scenario("it names the collection and the id it could not find") {
                withNotes { notes ->
                    val failure = shouldThrow<DocumentNotFoundException> { notes.requireById("missing") }

                    failure.collection shouldBe "notes"
                    failure.id shouldBe "missing"
                }
            }
        }

        feature("reading inside a session").config(enabled = MongoTestCluster.available) {
            scenario("a read in the transaction sees the transaction's own uncommitted write") {
                withNotesAndClient { notes, client ->
                    client.withTransaction { session ->
                        notes.insert(Note("n1", "pending"), session = session)

                        notes.findById("n1", session)?.text shouldBe "pending"
                        notes.findById("n1") shouldBe null
                    }
                }
            }
        }
    })
