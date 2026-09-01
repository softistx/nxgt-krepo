package com.strange.telemetry.trace

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A trace's id: 16 bytes, as 32 lower-case hex characters.
 *
 * Hex rather than bytes because that is what it is on the wire in every direction — the `traceparent`
 * header, an OTLP document, a log line somebody greps — and converting at each boundary would be
 * three conversions to save nothing.
 */
@Serializable
@JvmInline
value class TraceId(
    val hex: String,
) {
    val isValid: Boolean get() = hex.length == 32 && hex.any { it != '0' } && hex.all { it.isHex() }

    override fun toString(): String = hex

    companion object {
        val INVALID = TraceId("0".repeat(32))

        fun random(): TraceId = TraceId(Random.nextLong().hex() + Random.nextLong().hex())
    }
}

/** A span's id: 8 bytes, as 16 lower-case hex characters. */
@Serializable
@JvmInline
value class SpanId(
    val hex: String,
) {
    val isValid: Boolean get() = hex.length == 16 && hex.any { it != '0' } && hex.all { it.isHex() }

    override fun toString(): String = hex

    companion object {
        val INVALID = SpanId("0".repeat(16))

        fun random(): SpanId = SpanId(Random.nextLong().hex())
    }
}

/**
 * Which trace this is, which span within it, and whether anybody is keeping it.
 *
 * [sampled] is decided **once, by the root span**, and inherited unchanged by every span under it —
 * see [Sampler]. A trace half of whose spans were kept is not a trace; it is a set of orphans that
 * looks like a bug in whatever produced it.
 *
 * [remote] says the parent arrived over the wire rather than being a span in this process. It is
 * what lets an exporter mark the boundary where a trace entered this service.
 */
@Serializable
data class SpanContext(
    val traceId: TraceId,
    val spanId: SpanId,
    val sampled: Boolean,
    val remote: Boolean = false,
) {
    /** This context as a W3C `traceparent` header value. */
    fun traceparent(): String = "00-$traceId-$spanId-${if (sampled) "01" else "00"}"

    companion object {
        /**
         * Reads a W3C `traceparent`, or null when there is nothing usable in it.
         *
         * Null rather than an exception, and this is the whole of the error handling on purpose: a
         * malformed header is somebody else's bug arriving over the network, and the useful response
         * is to start a fresh trace rather than to fail a request that is otherwise fine.
         *
         * A version other than `00` is **not** rejected. The specification says a future version
         * keeps the first four fields, so reading them and ignoring the rest is what it asks for —
         * refusing would make this library the reason an upgraded caller loses its traces.
         */
        fun traceparent(header: String?): SpanContext? {
            val parts = header?.trim()?.split('-') ?: return null
            if (parts.size < 4 || parts[0].length != 2 || parts[0] == "ff") return null
            val traceId = TraceId(parts[1].lowercase())
            val spanId = SpanId(parts[2].lowercase())
            if (!traceId.isValid || !spanId.isValid) return null
            val flags = parts[3].lowercase().toIntOrNull(16) ?: return null
            return SpanContext(traceId, spanId, sampled = flags and 0x01 == 0x01, remote = true)
        }
    }
}

private fun Char.isHex(): Boolean = this in '0'..'9' || this in 'a'..'f'

private fun Long.hex(): String = toULong().toString(16).padStart(16, '0')
