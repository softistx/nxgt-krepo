package com.softistx.amqp.message

import com.rabbitmq.client.LongString

/**
 * A message's headers, with AMQP's own encoding kept out of the caller's way.
 *
 * The client hands back a `Map<String, Object>` in which a string is a [LongString] rather than a
 * `String`, so the obvious `headers["tenant"] as String` throws — reliably, in production, on the
 * one header somebody added last. [string] decodes instead of casting.
 *
 * A value class over the map: headers are read far more often than they are built, and this costs
 * nothing at runtime.
 */
@JvmInline
value class MessageHeaders(
    val values: Map<String, Any?> = emptyMap(),
) {
    val names: Set<String> get() = values.keys

    operator fun get(name: String): Any? = values[name]

    operator fun contains(name: String): Boolean = name in values

    /** The header as text, whichever of AMQP's string shapes it arrived in. */
    fun string(name: String): String? =
        when (val value = values[name]) {
            null -> null
            is LongString -> value.toString()
            is ByteArray -> value.decodeToString()
            else -> value.toString()
        }

    fun long(name: String): Long? = (values[name] as? Number)?.toLong()

    fun int(name: String): Int? = (values[name] as? Number)?.toInt()

    /** These plus [other], for adding a header to a message being forwarded. */
    operator fun plus(other: Map<String, Any?>): MessageHeaders = MessageHeaders(values + other)

    /** What the client wants when publishing: nulls dropped, since AMQP has no null header value. */
    fun asPublished(): Map<String, Any> = values.filterValues { it != null }.mapValues { (_, value) -> value!! }

    companion object {
        val EMPTY = MessageHeaders()
    }
}

/** `headersOf("tenant" to "acme", "attempt" to 2)`. */
fun headersOf(vararg headers: Pair<String, Any?>): MessageHeaders = MessageHeaders(headers.toMap())
