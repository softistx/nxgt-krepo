package com.strange.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What a value written by another service, or by another version of this one, is read with.
 *
 * Lenient about unknown keys, because of who wrote it: a stored value or a message is produced by
 * whoever deployed last and read by whoever deployed first. A reader that throws on a field a newer
 * writer added is a reader that stops during every rolling deploy — and it stops on data that is
 * perfectly good, having failed to ignore the one part of it that it does not know about.
 *
 * Strictness belongs on the way *in*, where the writer can still be fixed, not on the way out of a
 * cache, a topic or a queue. Each library exposes this under its own name — `redisJson`,
 * `kafkaJson`, `amqpJson` — so a caller configures one `Json` per connection and not one per type.
 */
val lenientJson: Json =
    Json {
        ignoreUnknownKeys = true
    }

/**
 * Decodes [text], turning a `SerializationException` into whatever this layer calls that.
 *
 * Every typed layer over a wire format needs the same three lines and differs only in the exception
 * it throws, because the useful part of the failure is the *context*: which key, which topic and
 * offset, which queue and delivery. [onFailure] is where that is added.
 *
 * The text itself is deliberately not passed to [onFailure] — a stored value is somebody's payment
 * details as often as it is a test fixture, and an exception message ends up in a log nobody meant
 * to make sensitive. [typeName] is what a caller needs and all it needs.
 */
inline fun <T> Json.decodeValue(
    serializer: KSerializer<T>,
    text: String,
    onFailure: (SerializationException) -> Throwable,
): T =
    try {
        decodeFromString(serializer, text)
    } catch (failure: SerializationException) {
        throw onFailure(failure)
    }

/** What the type is called in a failure message — its `@SerialName` if it has one. */
val KSerializer<*>.typeName: String get() = descriptor.serialName
