package com.strange.telemetry

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The named values carried alongside a log or a span.
 *
 * **An attribute is a scalar.** A string, a number, a boolean, or a list of those — that is what a
 * backend can index, filter and group by, and it is what OTLP accepts. Anything with structure
 * belongs in a `@Serializable` event type instead, where its shape is declared once and its fields
 * become attributes by name; see [Logger].
 *
 * That rule is also the answer to the question every logging library eventually gets wrong. A
 * `toString()` on a domain object puts every field it happens to have into a log line — including
 * the ones added next quarter, including the card number — and nobody finds out. Declaring the type
 * you log makes *choosing what is logged* the same act as writing the code, rather than a redaction
 * list somebody has to keep up to date. `stx-common`'s decoding helper makes the same argument about
 * the text it deliberately never puts in an exception message.
 */
@Serializable
@JvmInline
value class Attributes(
    val values: Map<String, JsonElement> = emptyMap(),
) {
    val isEmpty: Boolean get() = values.isEmpty()

    operator fun plus(other: Attributes): Attributes =
        when {
            other.isEmpty -> this
            isEmpty -> other
            else -> Attributes(values + other.values)
        }

    companion object {
        val EMPTY = Attributes()
    }
}

/**
 * `attributesOf("orderId" to order.id, "attempt" to n)`.
 *
 * A value that is not a scalar is rendered with `toString()` rather than refused, because **a log
 * call must never be the thing that fails a request**. The guard against that being a mistake is the
 * documented rule above, not a runtime check.
 */
fun attributesOf(vararg pairs: Pair<String, Any?>): Attributes =
    if (pairs.isEmpty()) Attributes.EMPTY else Attributes(pairs.associate { (key, value) -> key to attribute(value) })

internal fun attribute(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Enum<*> -> JsonPrimitive(value.name)
        is Instant -> JsonPrimitive(value.toString())
        is Duration -> JsonPrimitive(value.toString())
        is Iterable<*> -> JsonArray(value.map { attribute(it) })
        is Array<*> -> JsonArray(value.map { attribute(it) })
        else -> JsonPrimitive(value.toString())
    }
