package com.strange.amqp.codec

import com.strange.amqp.AmqpValueException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * How a body becomes bytes, and back.
 *
 * AMQP carries an opaque byte array and a content type beside it; this pairs the two so a publisher
 * and a consumer cannot disagree about which is which. Typed publishers and consumers hold one of
 * these underneath, so the reified `<T>` factories are a convenience rather than a wall.
 */
interface AmqpCodec<T> {
    /** What goes in the message's `content-type`, so a consumer in another language knows what it got. */
    val contentType: String

    fun encode(value: T): ByteArray

    fun decode(bytes: ByteArray): T

    companion object {
        /** The body as it arrived. */
        val bytes: AmqpCodec<ByteArray> =
            object : AmqpCodec<ByteArray> {
                override val contentType = "application/octet-stream"

                override fun encode(value: ByteArray) = value

                override fun decode(bytes: ByteArray) = bytes
            }

        /** UTF-8 text — for a queue shared with something that does not speak JSON. */
        val text: AmqpCodec<String> =
            object : AmqpCodec<String> {
                override val contentType = "text/plain"

                override fun encode(value: String) = value.toByteArray()

                override fun decode(bytes: ByteArray) = bytes.decodeToString()
            }

        /** A kotlinx.serialization type as JSON. */
        fun <T> json(
            serializer: KSerializer<T>,
            json: Json = amqpJson,
        ): AmqpCodec<T> = JsonCodec(serializer, json)
    }
}

/** [AmqpCodec.json] for a type that can be reified. */
inline fun <reified T> jsonCodec(json: Json = amqpJson): AmqpCodec<T> = AmqpCodec.json(json.serializersModule.serializer(), json)

internal class JsonCodec<T>(
    private val serializer: KSerializer<T>,
    private val json: Json,
) : AmqpCodec<T> {
    override val contentType = "application/json"

    override fun encode(value: T): ByteArray = json.encodeToString(serializer, value).toByteArray()

    override fun decode(bytes: ByteArray): T =
        try {
            json.decodeFromString(serializer, bytes.decodeToString())
        } catch (failure: Exception) {
            /* The bytes themselves are deliberately not in the message: a body is somebody's
               payment details as often as it is a test fixture. */
            throw AmqpValueException(
                "a message body is not a valid ${serializer.descriptor.serialName}",
                cause = failure,
            )
        }
}
