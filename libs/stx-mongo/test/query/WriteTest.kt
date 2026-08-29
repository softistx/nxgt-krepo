package com.strange.mongo.query

import com.mongodb.client.model.Updates
import com.strange.mongo.Draft
import com.strange.mongo.MongoTestCluster
import com.strange.mongo.Note
import com.strange.mongo.withDrafts
import com.strange.mongo.withNotes
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.count
import org.bson.BsonDocument

/**
 * Writes. The scenario worth the words is `findByIdAndUpdate`: the driver hands back the document
 * as it was *before*, and these helpers deliberately do not.
 */
class WriteTest :
    FeatureSpec({

        feature("inserting").config(enabled = MongoTestCluster.available) {
            scenario("one, and many") {
                withNotes { notes ->
                    notes.insert(Note("n1", "one"))
                    notes.insertAll(listOf(Note("n2", "two"), Note("n3", "three")))

                    notes.findAll().count() shouldBe 3
                }
            }

            scenario("inserting nothing is a no-op, where the driver would refuse") {
                withNotes { notes ->
                    notes.insertAll(emptyList()) shouldBe null
                    notes.findAll().count() shouldBe 0
                }
            }

            scenario("insertAndRead answers with the document as the collection now holds it") {
                withNotes { notes ->
                    notes.insertAndRead(Note("n1", "one")).text shouldBe "one"
                    notes.findAll().count() shouldBe 1
                }
            }

            // The case that reading an id off the document in hand could never reach: the document
            // has no id to read until the server has answered.
            scenario("insertAndRead reads back an _id the server generated, which no document carried") {
                withDrafts { drafts ->
                    val stored = drafts.insertAndRead(Draft(text = "unsent"))

                    val id = stored.id.shouldNotBeNull()
                    stored.text shouldBe "unsent"
                    drafts.findById(id)?.text shouldBe "unsent"
                }
            }
        }

        feature("updating").config(enabled = MongoTestCluster.available) {
            scenario("updateById touches exactly one document") {
                withNotes { notes ->
                    notes.insertAll(listOf(Note("n1", "one"), Note("n2", "two")))

                    notes.updateById("n1", Updates.set("text", "edited")).modifiedCount shouldBe 1
                    notes.findById("n1")?.text shouldBe "edited"
                    notes.findById("n2")?.text shouldBe "two"
                }
            }

            scenario("updateAll touches every match") {
                withNotes { notes ->
                    notes.insertAll(listOf(Note("n1", "one"), Note("n2", "two")))

                    notes.updateAll(BsonDocument(), Updates.set("tag", "archived")).modifiedCount shouldBe 2
                }
            }

            scenario("findByIdAndUpdate answers with the document as it now is") {
                withNotes { notes ->
                    notes.insert(Note("n1", "one"))

                    notes.findByIdAndUpdate("n1", Updates.set("text", "edited"))?.text shouldBe "edited"
                    notes.findByIdAndUpdate("missing", Updates.set("text", "edited")) shouldBe null
                }
            }
        }

        feature("deleting").config(enabled = MongoTestCluster.available) {
            scenario("by id, by ids, and by filter") {
                withNotes { notes ->
                    notes.insertAll(listOf(Note("n1", "one"), Note("n2", "two"), Note("n3", "three")))

                    notes.deleteById("n1").deletedCount shouldBe 1
                    notes.deleteByIds(listOf("n2", "n3")).deletedCount shouldBe 2
                    notes.findAll().count() shouldBe 0
                }
            }

            scenario("findByIdAndDelete hands back what it removed") {
                withNotes { notes ->
                    notes.insert(Note("n1", "one"))

                    notes.findByIdAndDelete("n1")?.text shouldBe "one"
                    notes.findByIdAndDelete("n1") shouldBe null
                }
            }
        }
    })
