package com.strange.redis.codec

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * How a value becomes the string Redis stores, and back.
 *
 * One interface rather than a `KSerializer` everywhere, because not every value wants to be JSON: a
 * cache of strings should hold `hello`, not `"hello"`, and a counter read by another service should
 * be a number that `INCR` can touch. [string] and [json] are those two answers.
 */
interface ValueCodec<T> {
    fun encode(value: T): String

    fun decode(raw: String): T

    companion object {
        /** The value as itself — no quoting, no escaping, nothing another client has to undo. */
        val string: ValueCodec<String> =
            object : ValueCodec<String> {
                override fun encode(value: String): String = value

                override fun decode(raw: String): String = raw
            }

        /** The value as its kotlinx.serialization JSON form. */
        inline fun <reified T> json(json: Json = Json): ValueCodec<T> = JsonValueCodec(json, serializer())

        fun <T> json(
            json: Json,
            serializer: KSerializer<T>,
        ): ValueCodec<T> = JsonValueCodec(json, serializer)
    }
}
