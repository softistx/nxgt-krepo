package com.strange.kafka.producer

/**
 * What the producer does to a batch before it goes over the wire.
 *
 * Compression happens per batch, not per record, so it pays for itself in proportion to how well
 * the producer is batching — a `linger` of zero on a quiet topic compresses batches of one and
 * mostly buys overhead. The broker stores what it is given and the consumer decompresses, so this
 * is also a decision about disk and about replication traffic, not only about this producer's
 * network.
 *
 * [Zstd] is the modern default worth reaching for: better ratios than gzip at a fraction of its
 * cost. [Lz4] is the choice when CPU is the scarce thing and [None] when the payload is already
 * compressed.
 */
enum class Compression(
    internal val value: String,
) {
    None("none"),
    Gzip("gzip"),
    Snappy("snappy"),
    Lz4("lz4"),
    Zstd("zstd"),
}
