package com.softistx.kafka.record

import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders as KafkaHeaders

/**
 * A record's headers, as they actually are: bytes, and repeatable.
 *
 * A `Map<String, String>` is the shape everyone wants and the shape Kafka does not have. Two headers
 * can share a name — that is how a retry count and a trace context accumulate down a chain of
 * services — and a value can be anything, because the broker never looks inside one. This keeps both
 * truths and hands out the convenient view through [get] and [toMap], which take the *last* value
 * for a name: a header added later in the chain is the one that meant to win.
 */
@JvmInline
value class RecordHeaders(
    val entries: List<Pair<String, ByteArray>> = emptyList(),
) {
    /** The last value for [name] as UTF-8, or null when there is none. */
    operator fun get(name: String): String? = bytes(name)?.decodeToString()

    fun bytes(name: String): ByteArray? = entries.lastOrNull { it.first == name }?.second

    /** Every value for [name], oldest first — the case a map cannot express. */
    fun all(name: String): List<String> = entries.filter { it.first == name }.map { it.second.decodeToString() }

    operator fun contains(name: String): Boolean = entries.any { it.first == name }

    fun toMap(): Map<String, String> = entries.associate { it.first to it.second.decodeToString() }

    operator fun plus(header: Pair<String, String>): RecordHeaders =
        RecordHeaders(entries + (header.first to header.second.encodeToByteArray()))

    internal fun toKafka(): KafkaHeaders =
        KafkaHeaders().also { headers -> entries.forEach { (name, value) -> headers.add(RecordHeader(name, value)) } }

    companion object {
        val EMPTY = RecordHeaders()

        internal fun from(headers: Iterable<Header>): RecordHeaders =
            RecordHeaders(headers.map { it.key() to (it.value() ?: ByteArray(0)) })
    }
}

/** `headersOf("trace-id" to id)` — the shape a caller wants when every value is a string. */
fun headersOf(vararg headers: Pair<String, String>): RecordHeaders =
    RecordHeaders(headers.map { it.first to it.second.encodeToByteArray() })
