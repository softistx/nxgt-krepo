package com.strange.mongo.query

import com.mongodb.MongoWriteException
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.strange.mongo.MongoTestCluster
import com.strange.mongo.Note
import com.strange.mongo.withNotes
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The conflict case is the reason `ensureIndex` exists at all: a service that declares its indexes
 * on startup must not die because an older deployment declared one of them differently, and must
 * not silently rebuild it either.
 */
class IndexesTest :
    FeatureSpec({

        feature("ensureUniqueIndex").config(enabled = MongoTestCluster.available) {
            scenario("it creates the index, and the constraint is real") {
                withNotes { notes ->
                    notes.ensureUniqueIndex(Indexes.ascending("text")) shouldNotBe null
                    notes.insert(Note("n1", "one"))

                    shouldThrow<MongoWriteException> { notes.insert(Note("n2", "one")) }
                }
            }

            scenario("declaring the same index again is a no-op, not an error") {
                withNotes { notes ->
                    notes.ensureUniqueIndex(Indexes.ascending("text")) shouldNotBe null
                    notes.ensureUniqueIndex(Indexes.ascending("text")) shouldNotBe null
                }
            }
        }

        feature("an index that is already there, declared differently").config(enabled = MongoTestCluster.available) {
            scenario("the existing one stands and the caller is told with null") {
                withNotes { notes ->
                    notes.ensureIndex(Indexes.ascending("text"), IndexOptions().name("by-text"))

                    notes.ensureIndex(Indexes.ascending("text"), IndexOptions().name("by-text-again")) shouldBe null
                }
            }
        }
    })
