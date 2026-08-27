package com.strange.kafka.producer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What a publisher promises about the records it sends.
 *
 * The two defaults that matter are stated rather than inherited. [acks] `all` means the broker
 * answers only once every in-sync replica has the record — on a cluster with `min.insync.replicas`
 * above one, that is the difference between a write that survives a broker loss and one that looked
 * like it did. [idempotent] stops a retry inside the client from writing the record twice, which is
 * what "at least once" would otherwise mean at this level. Both are Kafka's own defaults today;
 * spelling them here keeps the guarantee visible and immune to a future default changing under it.
 *
 * [linger] is the throughput knob: a producer that waits a few milliseconds before sending fills
 * batches instead of sending one record per request. Zero — Kafka's default — is the latency
 * choice, and the right one until a profile says otherwise.
 */
data class PublisherOptions(
    val acks: String = "all",
    val idempotent: Boolean = true,
    val linger: Duration = Duration.ZERO,
    val compression: String? = null,
    val maxBlock: Duration = 60.seconds,
    val properties: Map<String, String> = emptyMap(),
)
