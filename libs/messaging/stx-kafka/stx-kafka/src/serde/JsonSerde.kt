package com.softistx.kafka.serde

import com.softistx.common.serialization.decodeValue
import com.softistx.common.serialization.typeName
import com.softistx.kafka.KafkaValueException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer

/** A value as its kotlinx.serialization JSON form, in UTF-8. */
class JsonSerializer<T>(
    private val json: Json,
    private val serializer: KSerializer<T>,
) : Serializer<T> {
    override fun serialize(
        topic: String,
        data: T?,
    ): ByteArray? = data?.let { json.encodeToString(serializer, it).encodeToByteArray() }
}

/**
 * The other half, which is where the failures live.
 *
 * A decode failure becomes [KafkaValueException] rather than staying a `SerializationException`,
 * because of where it happens: the bytes were written by another service, or by an older version of
 * this one, and what the operator needs to know is which topic they came from. Kafka wraps whatever
 * this throws in a `RecordDeserializationException` carrying the partition and offset, so the
 * consumer can say exactly which record it could not read.
 */
class JsonDeserializer<T>(
    private val json: Json,
    private val serializer: KSerializer<T>,
) : Deserializer<T> {
    override fun deserialize(
        topic: String,
        data: ByteArray?,
    ): T? =
        data?.let {
            json.decodeValue(serializer, it.decodeToString()) { failure ->
                KafkaValueException("a record on '$topic' is not a ${serializer.typeName}", cause = failure)
            }
        }
}

/** The JSON pair for [T], through [kafkaJson] unless told otherwise. */
inline fun <reified T> jsonSerde(json: Json = kafkaJson): KafkaSerde<T> = jsonSerde(json, serializer())

fun <T> jsonSerde(
    json: Json,
    serializer: KSerializer<T>,
): KafkaSerde<T> = KafkaSerde(JsonSerializer(json, serializer), JsonDeserializer(json, serializer))
