package com.strange.mongo.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.strange.mongo.DocumentNotFoundException
import com.strange.mongo.Draft
import com.strange.mongo.MongoTestCluster
import com.strange.mongo.Note
import com.strange.mongo.page.PaginationOptions
import com.strange.mongo.query.ensureUniqueIndex
import com.strange.mongo.withNotesDatabase
import com.strange.mongo.withNotesDatabaseAndClient
import com.strange.mongo.withTransaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId

private fun repository(database: MongoDatabase) = mongoRepository<Note, String>(database, "notes")

/** The subclass in the KDoc, compiled — a repository that adds a query and declares its indexes. */
private class NoteRepository(
    database: MongoDatabase,
) : MongoCrudRepository<Note, String>(database, "notes", Note::class) {
    suspend fun findByTag(tag: String) = findAll(Filters.eq("tag", tag)).toList()

    override suspend fun ensureIndexes() {
        collection.ensureUniqueIndex(Indexes.ascending("text"))
    }
}

class MongoCrudRepositoryTest :
    FeatureSpec({

        val notes = listOf(Note("a", "first", tag = "x"), Note("b", "second", tag = "y"))

        feature("reading").config(enabled = MongoTestCluster.available) {
            scenario("by id, by ids, by filter, and by page") {
                withNotesDatabase { database ->
                    val repository = repository(database)
                    repository.insertAll(notes)

                    repository.findById("a")?.text shouldBe "first"
                    repository.findById("missing") shouldBe null
                    repository.findByIds(listOf("a", "b")).toList().map { it.id } shouldContainExactlyInAnyOrder
                        listOf("a", "b")
                    repository.findOne(Filters.eq("tag", "y"))?.id shouldBe "b"
                    repository.findPage(PaginationOptions.first(1)).data.map { it.id } shouldBe listOf("a")
                }
            }

            scenario("counting and existence, without loading anything") {
                withNotesDatabase { database ->
                    val repository = repository(database)
                    repository.insertAll(notes)

                    repository.count() shouldBe 2
                    repository.count(Filters.eq("tag", "x")) shouldBe 1
                    repository.exists(Filters.eq("tag", "x")) shouldBe true
                    repository.existsById("a") shouldBe true
                    repository.existingIds(listOf("b", "missing", "a")) shouldBe listOf("b", "a")
                }
            }

            scenario("requireById names what it could not find") {
                withNotesDatabase { database ->
                    val failure = shouldThrow<DocumentNotFoundException> { repository(database).requireById("missing") }

                    failure.collection shouldBe "notes"
                }
            }
        }

        feature("writing").config(enabled = MongoTestCluster.available) {
            scenario("an update answers with the document as it now stands") {
                withNotesDatabase { database ->
                    val repository = repository(database)
                    repository.insert(notes.first())

                    repository.updateById("a", Updates.set("text", "edited"))?.text shouldBe "edited"
                    repository.updateById("missing", Updates.set("text", "edited")) shouldBe null
                    repository.updateOne(Filters.eq("tag", "x"), Updates.set("tag", "z"))?.tag shouldBe "z"
                }
            }

            scenario("a delete says whether there was anything to delete") {
                withNotesDatabase { database ->
                    val repository = repository(database)
                    repository.insertAll(notes)

                    repository.deleteById("a") shouldBe true
                    repository.deleteById("a") shouldBe false
                    repository.deleteByIds(listOf("b", "missing")) shouldBe 1
                }
            }
        }

        feature("the same repository inside a transaction").config(enabled = MongoTestCluster.available) {
            scenario("passing a session is the only difference at the call site") {
                withNotesDatabaseAndClient { database, client ->
                    val repository = repository(database)

                    shouldThrow<IllegalStateException> {
                        client.withTransaction { session ->
                            repository.insert(notes.first(), session)
                            repository.findById("a", session)?.text shouldBe "first"
                            error("no")
                        }
                    }

                    repository.count() shouldBe 0
                }
            }
        }

        feature("how it is built").config(enabled = MongoTestCluster.available) {
            // The point of the change: a repository asks for the database, so a container that has
            // one can build it — rather than asking for a collection, which pushes the name and the
            // document class out to whoever does the wiring.
            scenario("from a database, resolving its own collection") {
                withNotesDatabase { database ->
                    val repository = repository(database)
                    repository.insertAll(notes)

                    repository.name shouldBe "notes"
                    repository.collection.namespace.collectionName shouldBe "notes"
                    repository.count() shouldBe 2
                }
            }

            scenario("or from the cluster, with the database named beside it") {
                withNotesDatabaseAndClient { database, client ->
                    val repository =
                        mongoRepository<Note, String>(client, database.name, "notes")
                    repository.insert(notes.first())

                    repository.findById("a")?.text shouldBe "first"
                }
            }
        }

        feature("inserting and reading back").config(enabled = MongoTestCluster.available) {
            scenario("answers with the document as the collection now holds it") {
                withNotesDatabase { database ->
                    val repository = repository(database)

                    repository.insertAndRead(notes.first()).text shouldBe "first"
                    repository.count() shouldBe 1
                }
            }

            // The case the old design could not reach at all: it read the id off the document in
            // hand, so a document that had none had nothing to read back with.
            scenario("including an _id the server generated, which no document carried") {
                withNotesDatabase { database ->
                    val drafts = mongoRepository<Draft, ObjectId>(database, "drafts")

                    val stored = drafts.insertAndRead(Draft(text = "unsent"))

                    val id = stored.id.shouldNotBeNull()
                    stored.text shouldBe "unsent"
                    drafts.findById(id)?.text shouldBe "unsent"
                }
            }
        }

        feature("a subclass").config(enabled = MongoTestCluster.available) {
            scenario("it adds its own queries and declares its own indexes") {
                withNotesDatabase { database ->
                    val repository = NoteRepository(database)
                    repository.ensureIndexes()
                    repository.insertAll(notes)

                    repository.findByTag("y").map { it.id } shouldBe listOf("b")
                    repository.name shouldBe "notes"
                }
            }
        }
    })
