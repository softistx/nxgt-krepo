package com.softistx.mongo.codec

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.BsonType
import org.bson.codecs.EncoderContext
import kotlin.time.Instant

@Serializable
private data class Event(
    val id: String,
    @Contextual val at: Instant,
)

/**
 * The same class is stored and served. These two scenarios are the whole reason the serializer
 * branches on its encoder: one asserts Mongo gets a date it can index, the other that a client gets
 * a string it can parse.
 */
class InstantAsBsonDateTimeTest :
    FeatureSpec({

        val at = Instant.fromEpochMilliseconds(1_700_000_000_000)

        feature("an entity field, through the registry") {
            scenario("it is stored as a BSON date") {
                val document = BsonDocument()
                BsonDocumentWriter(document).use { writer ->
                    mongoCodecRegistry()
                        .get(Event::class.java)
                        .encode(writer, Event("e1", at), EncoderContext.builder().build())
                }

                document.getValue("at").bsonType shouldBe BsonType.DATE_TIME
                document.getString("id").value shouldBe "e1"
            }
        }

        feature("the same class outside Mongo") {
            scenario("JSON gets ISO-8601, because kotlinx's encoder has no date type") {
                val json = Json { serializersModule = mongoSerializersModule }

                json.encodeToString(Event("e1", at)) shouldContain """"at":"2023-11-14T22:13:20Z""""
                json.decodeFromString<Event>("""{"id":"e1","at":"2023-11-14T22:13:20Z"}""").at shouldBe at
            }
        }
    })
