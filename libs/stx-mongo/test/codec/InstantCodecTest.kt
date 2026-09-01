package com.softistx.mongo.codec

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.BsonType
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import kotlin.time.Instant

/**
 * A timestamp stored as a string sorts lexicographically and compares against the wrong thing in a
 * range query. The only assertion that catches that is the BSON *type* on the wire, which is why
 * these tests look at the document rather than at a round-tripped value.
 */
class InstantCodecTest :
    FeatureSpec({

        val codec = InstantCodec()

        fun encode(value: Instant): BsonDocument {
            val document = BsonDocument()
            BsonDocumentWriter(document).use { writer ->
                writer.writeStartDocument()
                writer.writeName("at")
                codec.encode(writer, value, EncoderContext.builder().build())
                writer.writeEndDocument()
            }
            return document
        }

        feature("what the codec puts on the wire") {
            scenario("a BSON date, not a string") {
                encode(Instant.fromEpochMilliseconds(1_700_000_000_000)).getValue("at").bsonType shouldBe BsonType.DATE_TIME
            }

            scenario("it reads back what it wrote") {
                val value = Instant.fromEpochMilliseconds(1_700_000_000_000)
                val reader = encode(value).asBsonReader()

                reader.readStartDocument()
                reader.readName()

                codec.decode(reader, DecoderContext.builder().build()) shouldBe value
            }
        }

        feature("the precision BSON has") {
            scenario("sub-millisecond detail does not survive, because a BSON date has none") {
                val value = Instant.fromEpochSeconds(1_700_000_000, nanosecondAdjustment = 999_999)
                val reader = encode(value).asBsonReader()

                reader.readStartDocument()
                reader.readName()

                codec.decode(reader, DecoderContext.builder().build()) shouldBe Instant.fromEpochSeconds(1_700_000_000)
            }
        }
    })
