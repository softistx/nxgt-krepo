package com.softistx.kafka.admin

/**
 * What a topic actually is on this cluster, as opposed to what it was asked to be.
 *
 * [PartitionInfo.inSync] is the part worth having: a partition can have three replicas and only one
 * of them caught up, and on a cluster with `min.insync.replicas` above that number every `acks=all`
 * write to it is already failing. The replica list says what was configured; the in-sync list says
 * what is true right now.
 */
data class TopicInfo(
    val name: String,
    val partitions: List<PartitionInfo>,
) {
    val partitionCount: Int get() = partitions.size

    /** The smallest replica count across partitions — the one a write has to satisfy. */
    val replicationFactor: Int get() = partitions.minOfOrNull { it.replicas.size } ?: 0

    /** Partitions whose in-sync set has fallen below their replica set. */
    val underReplicated: List<PartitionInfo> get() = partitions.filter { it.inSync.size < it.replicas.size }
}

data class PartitionInfo(
    val partition: Int,
    val leader: Int?,
    val replicas: List<Int>,
    val inSync: List<Int>,
)
