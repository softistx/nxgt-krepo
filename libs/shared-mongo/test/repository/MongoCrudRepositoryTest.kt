package com.strange.mongo.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.strange.mongo.DocumentNotFoundException
import com.strange.mongo.MongoTestCluster
import com.strange.mongo.Note
import com.strange.mongo.page.PaginationOptions
import com.strange.mongo.query.ensureUniqueIndex
import com.strange.mongo.withNotes
import com.strange.mongo.withNotesAndClient
import com.strange.mongo.withTransaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList

private fun repository(collection: MongoCollection<Note>) = MongoCrudRepository(collection, Note::id)

/** The subclass in the KDoc, compiled — a repository that adds a query and declares its indexes. */
private class NoteRepository(
    collection: MongoCollection<Note>,
) : MongoCrudRepository<Note, String>(collection, Note::id) {
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
                withNotes { collection ->
                    val repository = repository(collection)
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
                withNotes { collection ->
                    val repository = repository(collection)
                    repository.insertAll(notes)

                    repository.count() shouldBe 2
                    repository.count(Filters.eq("tag", "x")) shouldBe 1
                    repository.exists(Filters.eq("tag", "x")) shouldBe true
                    repository.existsById("a") shouldBe true
                    repository.existingIds(listOf("b", "missing", "a")) shouldBe listOf("b", "a")
                }
            }

            scenario("requireById names what it could not find") {
                withNotes { collection ->
                    val failure = shouldThrow<DocumentNotFoundException> { repository(collection).requireById("missing") }

                    failure.collection shouldBe "notes"
                }
            }
        }

        feature("writing").config(enabled = MongoTestCluster.available) {
            scenario("an update answers with the document as it now stands") {
                withNotes { collection ->
                    val repository = repository(collection)
                    repository.insert(notes.first())

                    repository.updateById("a", Updates.set("text", "edited"))?.text shouldBe "edited"
                    repository.updateById("missing", Updates.set("text", "edited")) shouldBe null
                    repository.updateOne(Filters.eq("tag", "x"), Updates.set("tag", "z"))?.tag shouldBe "z"
                }
            }

            scenario("a delete says whether there was anything to delete") {
                withNotes { collection ->
                    val repository = repository(collection)
                    repository.insertAll(notes)

                    repository.deleteById("a") shouldBe true
                    repository.deleteById("a") shouldBe false
                    repository.deleteByIds(listOf("b", "missing")) shouldBe 1
                }
            }
        }

        feature("the same repository inside a transaction").config(enabled = MongoTestCluster.available) {
            scenario("passing a session is the only difference at the call site") {
                withNotesAndClient { collection, client ->
                    val repository = repository(collection)

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

        feature("a subclass").config(enabled = MongoTestCluster.available) {
            scenario("it adds its own queries and declares its own indexes") {
                withNotes { collection ->
                    val repository = NoteRepository(collection)
                    repository.ensureIndexes()
                    repository.insertAll(notes)

                    repository.findByTag("y").map { it.id } shouldBe listOf("b")
                    repository.name shouldBe "notes"
                }
            }
        }
    })
