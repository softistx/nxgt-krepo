package com.strange.mongo.query

import com.mongodb.client.model.Filters
import com.strange.mongo.MongoTestCluster
import com.strange.mongo.Note
import com.strange.mongo.withNotes
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList

/**
 * `existingIds` is the interesting one: a service validating a list of references calls it on every
 * write, so the assertions cover the contract callers depend on — the order they passed in, and
 * only the ids that are really there.
 */
class ExistsTest :
    FeatureSpec({

        feature("exists").config(enabled = MongoTestCluster.available) {
            scenario("it answers on a filter and on an id") {
                withNotes { notes ->
                    notes.insert(Note("n1", "one"))

                    notes.exists(Filters.eq("text", "one")) shouldBe true
                    notes.exists(Filters.eq("text", "other")) shouldBe false
                    notes.existsById("n1") shouldBe true
                    notes.existsById("missing") shouldBe false
                }
            }
        }

        feature("existingIds").config(enabled = MongoTestCluster.available) {
            scenario("it keeps the caller's order and drops what is missing") {
                withNotes { notes ->
                    notes.insertAll(listOf(Note("n1", "one"), Note("n2", "two"), Note("n3", "three")))

                    notes.existingIds(listOf("n3", "missing", "n1")) shouldBe listOf("n3", "n1")
                }
            }

            scenario("an empty list asks the server nothing") {
                withNotes { notes ->
                    notes.existingIds(emptyList()) shouldBe emptyList()
                }
            }

            scenario("projecting only _id still works on an entity with required fields") {
                withNotes { notes ->
                    notes.insert(Note("n1", "one"))

                    notes.existingIds(listOf("n1")) shouldBe listOf("n1")
                    notes
                        .findAll()
                        .toList()
                        .single()
                        .text shouldBe "one"
                }
            }
        }
    })
