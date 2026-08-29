package com.strange.mongo.codec

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bson.BsonDateTime
import org.bson.codecs.kotlinx.BsonDecoder
import org.bson.codecs.kotlinx.BsonEncoder
import kotlin.time.Instant

/**
 * Serializes a [kotlin.time.Instant] entity field as a BSON `Date`, and as an ISO-8601 string
 * everywhere else.
 *
 * bson-kotlinx ships `InstantAsBsonDateTime`, but for `kotlinx.datetime.Instant` — a type that
 * kotlinx-datetime 0.7 turned into an alias of [kotlin.time.Instant], leaving the compiled
 * serializer bound to a class that no longer exists. This is the same idea against the stdlib type,
 * so nothing here needs kotlinx-datetime on the classpath.
 *
 * The fallback is the point of the `when`: the same `@Serializable` class is usually also what goes
 * out over HTTP, and there the encoder is kotlinx's JSON one, which has no notion of a BSON date.
 * Stored as a date so Mongo can compare and sort it, sent as a string so a client can read it.
 */
@OptIn(ExperimentalSerializationApi::class)
object InstantAsBsonDateTime : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.strange.mongo.codec.InstantAsBsonDateTime", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) {
        when (encoder) {
            is BsonEncoder -> encoder.encodeBsonValue(BsonDateTime(value.toEpochMilliseconds()))
            else -> encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): Instant =
        when (decoder) {
            is BsonDecoder -> Instant.fromEpochMilliseconds(decoder.decodeBsonValue().asDateTime().value)
            else -> Instant.parse(decoder.decodeString())
        }
}
