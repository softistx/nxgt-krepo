package com.strange.mongo.page

import com.mongodb.MongoClientSettings
import com.strange.mongo.InvalidPaginationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.bson.BsonDocument
import org.bson.conversions.Bson

private fun json(source: String) = Json.parseToJsonElement(source) as JsonObject

private fun Bson.render(): String = toBsonDocument(BsonDocument::class.java, MongoClientSettings.getDefaultCodecRegistry()).toJson()

/**
 * The ordering and the resume filter, without a server. These are the pieces a paging bug hides in:
 * a missing `_id` tiebreak, or an `$or` that compares the second key without pinning the first.
 */
class KeysetTest :
    FeatureSpec({

        feature("the ordering a page is cut along") {
            scenario("_id is appended, because a keyset has to be unique") {
                sortKeys(null) shouldBe listOf(SortKey("_id", ascending = true))
                sortKeys(json("""{"name": 1}""")) shouldBe
                    listOf(SortKey("name", ascending = true), SortKey("_id", ascending = true))
            }

            scenario("a sort that already names _id is left alone") {
                sortKeys(json("""{"_id": -1}""")) shouldBe listOf(SortKey("_id", ascending = false))
            }

            scenario("a direction that is not 1 or -1 is rejected") {
                shouldThrow<InvalidPaginationException> { sortKeys(json("""{"name": 2}""")) }
                shouldThrow<InvalidPaginationException> { sortKeys(json("""{"name": "asc"}""")) }
            }

            scenario("paging backward runs the whole ordering in reverse") {
                val keys = sortKeys(json("""{"name": 1}"""))

                sortOf(keys, forward = true).render() shouldBe """{"name": 1, "_id": 1}"""
                sortOf(keys, forward = false).render() shouldBe """{"name": -1, "_id": -1}"""
            }
        }

        feature("resuming from a cursor") {
            scenario("later keys are compared only where the earlier ones are equal") {
                val keys = sortKeys(json("""{"name": 1}"""))
                val cursor = BsonDocument.parse("""{"name": "b", "_id": "n2"}""")

                keysetFilter(cursor, keys, forward = true).render() shouldBe
                    """{"${'$'}or": [{"name": {"${'$'}gt": "b"}}, {"${'$'}and": [{"name": "b"}, {"_id": {"${'$'}gt": "n2"}}]}]}"""
            }

            scenario("backward flips every comparison") {
                val keys = sortKeys(null)
                val cursor = BsonDocument.parse("""{"_id": "n2"}""")

                keysetFilter(cursor, keys, forward = false).render() shouldContain """"${'$'}lt": "n2""""
            }
        }

        feature("a cursor from somewhere else") {
            scenario("one issued under a different sort is refused") {
                val keys = sortKeys(json("""{"name": 1}"""))
                val elsewhere = encodeCursor(BsonDocument.parse("""{"_id": "n1"}"""), sortKeys(null))

                shouldThrow<InvalidPaginationException> { decodeCursor(elsewhere, keys) }
            }

            scenario("one that is not a cursor at all is refused") {
                shouldThrow<InvalidPaginationException> { decodeCursor("not-a-cursor", sortKeys(null)) }
            }

            scenario("a cursor round-trips its BSON types") {
                val keys = sortKeys(json("""{"at": 1}"""))
                val cursor = BsonDocument.parse("""{"at": {"${'$'}date": "2023-11-14T22:13:20Z"}, "_id": "n1"}""")

                decodeCursor(encodeCursor(cursor, keys), keys) shouldBe cursor
            }
        }
    })
