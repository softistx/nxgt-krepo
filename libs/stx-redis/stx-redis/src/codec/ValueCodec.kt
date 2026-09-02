package com.softistx.redis.codec

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * How a value becomes the string Redis stores, and back — the seam underneath the typed layers.
 *
 * **This is the exception, not the way in.** A `@Serializable` type needs none of it: `redis.cache`,
 * `redis.topic`, `redis.topicPattern` and `redis.stream` take the type alone and serialize it with
 * kotlinx.serialization through the connection's `Json`. Reach for a codec when the value must
 * *not* be JSON — a cache of strings that should hold `hello` rather than `"hello"` because
 * `redis-cli` and another service read the same key, or a counter that has to stay a number
 * `INCR` can touch. [string] is the first of those; [json] is the same JSON the factories use,
 * for the rare call site that has to build one by hand.
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

        /** The value as its kotlinx.serialization JSON form, through [redisJson] unless told otherwise. */
        inline fun <reified T> json(json: Json = redisJson): ValueCodec<T> = JsonValueCodec(json, serializer())

        fun <T> json(
            json: Json,
            serializer: KSerializer<T>,
        ): ValueCodec<T> = JsonValueCodec(json, serializer)
    }
}
