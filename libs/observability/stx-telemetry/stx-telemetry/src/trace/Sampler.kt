package com.softistx.telemetry.trace

import kotlin.random.Random

/**
 * Whether a trace is kept.
 *
 * It is asked **once, for the root span**, and the answer travels with the trace: every span under
 * it inherits the decision, and a `traceparent` carries it to the next service. A sampler consulted
 * per span would produce traces missing their middles, which is worse than no trace at all — the gap
 * looks like the work never happened.
 *
 * The decision is by trace id rather than by a coin toss per service, so that every service in a
 * request path that uses the same ratio makes the *same* decision about the same trace. Two services
 * tossing independently at 10% keep a whole trace 1% of the time.
 */
fun interface Sampler {
    fun sample(traceId: TraceId): Boolean

    companion object {
        val always: Sampler = Sampler { true }

        /** Keeps nothing. Spans still run and still cost their attributes; nothing is exported. */
        val never: Sampler = Sampler { false }

        /**
         * Keeps [ratio] of traces, chosen by the trace id.
         *
         * The id's low 8 bytes are read as an unsigned number and compared against the ratio, which
         * is the same rule the OpenTelemetry specification gives — so a service using this and a
         * service using an OTel SDK at the same ratio agree about which traces to keep.
         */
        fun ratio(ratio: Double): Sampler {
            require(ratio in 0.0..1.0) { "ratio must be between 0.0 and 1.0, was $ratio" }
            if (ratio == 0.0) return never
            if (ratio == 1.0) return always
            val threshold = (ratio * ULong.MAX_VALUE.toDouble()).toULong()
            return Sampler { id ->
                val low = id.hex.takeLast(16).toULongOrNull(16) ?: Random.nextULong()
                low < threshold
            }
        }
    }
}

private fun Random.nextULong(): ULong = nextLong().toULong()
