package com.strange.redis.codec

import com.strange.redis.RedisValueException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A value as JSON.
 *
 * Decoding failure is turned into [RedisValueException] rather than left as a
 * `SerializationException`, because of where it happens: the value was written by an older version
 * of this service, or by another one entirely, and the caller reading it needs to know *which key*
 * it could not read. A stack trace pointing at kotlinx.serialization does not say.
 */
class JsonValueCodec<T>(
    private val json: Json,
    private val serializer: KSerializer<T>,
) : ValueCodec<T> {
    override fun encode(value: T): String = json.encodeToString(serializer, value)

    override fun decode(raw: String): T =
        try {
            json.decodeFromString(serializer, raw)
        } catch (e: SerializationException) {
            throw RedisValueException("stored value is not a ${serializer.descriptor.serialName}", e)
        }
}
