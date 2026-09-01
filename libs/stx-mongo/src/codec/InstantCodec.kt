package com.softistx.mongo.codec

import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import kotlin.time.Instant

/**
 * Reads and writes [kotlin.time.Instant] as a BSON `Date`.
 *
 * The driver has no codec for it, and the gap shows up outside entity mapping: the moment an
 * `Instant` is passed as a *value* — `Filters.gt("createdAt", now)`, `Updates.set(...)`, an
 * aggregation stage — the driver looks it up in the registry and fails with
 * `Can't find a codec for class kotlin.time.Instant`. Entity fields are a separate path, served by
 * [InstantAsBsonDateTime].
 *
 * BSON dates are milliseconds since the epoch, so anything finer is truncated on the way in and
 * does not come back.
 */
class InstantCodec : Codec<Instant> {
    override fun encode(
        writer: BsonWriter,
        value: Instant,
        encoderContext: EncoderContext,
    ) {
        writer.writeDateTime(value.toEpochMilliseconds())
    }

    override fun decode(
        reader: BsonReader,
        decoderContext: DecoderContext,
    ): Instant = Instant.fromEpochMilliseconds(reader.readDateTime())

    override fun getEncoderClass(): Class<Instant> = Instant::class.java
}
