package com.softistx.mongo.query

import com.mongodb.MongoClientSettings
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import org.bson.BsonDocument
import org.bson.conversions.Bson

private fun Bson.render(): String = toBsonDocument(BsonDocument::class.java, MongoClientSettings.getDefaultCodecRegistry()).toJson()

/**
 * The one place `_id` is spelled. These assert the rendered filter rather than that the helpers
 * return *something*, because a typo in the field name is exactly the failure they exist to stop.
 */
class IdsTest :
    FeatureSpec({

        feature("the filters this package builds") {
            scenario("byId matches the key field") {
                byId("n1").render() shouldBe """{"_id": "n1"}"""
            }

            scenario("byIds is a single \$in, not a chain of ors") {
                byIds(listOf("n1", "n2")).render() shouldBe """{"_id": {"${'$'}in": ["n1", "n2"]}}"""
            }

            scenario("no ids is an empty \$in, which matches nothing") {
                byIds(emptyList()).render() shouldBe """{"_id": {"${'$'}in": []}}"""
            }
        }
    })
