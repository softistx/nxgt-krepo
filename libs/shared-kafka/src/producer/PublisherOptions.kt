package com.strange.kafka.producer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What a publisher promises about the records it sends.
 *
 * The two defaults that matter are stated rather than inherited. [Acks.All] means the broker
 * answers only once every in-sync replica has the record; [idempotent] stops a retry inside the
 * client from writing the record twice, which is what "at least once" would otherwise mean at this
 * level. Both are Kafka's own defaults today; spelling them here keeps the guarantee visible and
 * immune to a future default changing under it.
 *
 * The two are not independent, and the pairing is checked here rather than left to the client:
 * an idempotent producer may only use [Acks.All], because a retry it cannot see acknowledged is a
 * retry it cannot deduplicate.
 *
 * [linger] is the throughput knob: a producer that waits a few milliseconds before sending fills
 * batches instead of sending one record per request. Zero — Kafka's default — is the latency
 * choice, and the right one until a profile says otherwise.
 */
data class PublisherOptions(
    val acks: Acks = Acks.All,
    val idempotent: Boolean = true,
    val linger: Duration = Duration.ZERO,
    val compression: Compression = Compression.None,
    val maxBlock: Duration = 60.seconds,
    val properties: Map<String, String> = emptyMap(),
) {
    init {
        require(!idempotent || acks == Acks.All) {
            "an idempotent producer requires Acks.All, not $acks — set idempotent = false to send with weaker acks"
        }
    }
}
