package com.softistx.kafka.producer

/**
 * How much of the cluster has to have a record before the broker says it has it.
 *
 * This is the durability dial, and the only one that changes what a successful send means:
 *
 * - [None] — the client does not wait for a reply at all. The fastest, and the one where "sent"
 *   means the bytes left this process. A broker that dies mid-write loses the record and nobody
 *   ever hears about it.
 * - [Leader] — the partition leader has written it. Survives everything except losing that leader
 *   before its followers catch up, which is exactly what a failover is.
 * - [All] — every in-sync replica has it. On a cluster with `min.insync.replicas` above one, this
 *   is the difference between a write that survives a broker loss and one that only looked like it
 *   did — and it is the only setting an idempotent producer may use.
 */
enum class Acks(
    internal val value: String,
) {
    None("0"),
    Leader("1"),
    All("all"),
}
