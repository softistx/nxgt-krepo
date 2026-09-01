package com.softistx.kafka.admin

import com.softistx.kafka.Kafka
import com.softistx.kafka.TopicNotFoundException
import com.softistx.kafka.clientProperties
import kotlinx.coroutines.future.await
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import java.util.Optional

/**
 * The operations that are about the cluster rather than about a message.
 *
 * Every call suspends: Kafka's `Admin` answers with a `KafkaFuture`, which this awaits through the
 * `CompletionStage` it can become, so nothing here parks a thread waiting on a round trip.
 *
 * The caller owns this and closes it — an admin client holds its own connections, and one shared
 * across an application is the right number.
 */
class KafkaAdmin internal constructor(
    internal val admin: Admin,
) : AutoCloseable {
    /** Every topic this credential can see; Kafka's own bookkeeping topics are hidden by default. */
    suspend fun topics(includeInternal: Boolean = false): Set<String> =
        admin
            .listTopics()
            .listings()
            .await()
            .filter { includeInternal || !it.isInternal }
            .map { it.name() }
            .toSet()

    suspend fun exists(topic: String): Boolean = describeOrNull(topic) != null

    /**
     * Creates [topic] if it is not already there, and answers whether this call is what created it.
     *
     * Idempotent by catching `TopicExists` rather than by asking first: two instances starting
     * together would both be told it does not exist and both try to create it, and the loser needs
     * this to succeed anyway.
     *
     * A null [partitions] or [replication] leaves the broker's own default in place, which is
     * usually what a deployment wants — the cluster, not the application, is where "how many
     * replicas" belongs.
     *
     * **`false` does not prove somebody else got there first.** The admin client retries a request
     * it did not hear back from, and a create that reached the controller before the timeout comes
     * back as `TopicExists` on the retry — so a caller that has just made up a unique name can still
     * be told the topic was already there. Use the answer for logging and for "did I have to do
     * anything", never as a lock: what is guaranteed on return is that the topic exists.
     */
    suspend fun ensureTopic(
        topic: String,
        partitions: Int? = null,
        replication: Short? = null,
        configs: Map<String, String> = emptyMap(),
    ): Boolean {
        val spec =
            NewTopic(topic, Optional.ofNullable(partitions), Optional.ofNullable(replication))
                .configs(configs)
        return try {
            admin.createTopics(listOf(spec)).all().await()
            true
        } catch (_: TopicExistsException) {
            false
        }
    }

    /** Removes [topic] and everything in it. Silent when it was not there — the end state is the same. */
    suspend fun deleteTopic(topic: String) {
        try {
            admin.deleteTopics(listOf(topic)).all().await()
        } catch (_: UnknownTopicOrPartitionException) {
            // already gone
        }
    }

    /** What [topic] looks like on the cluster, or a failure naming it. */
    suspend fun describe(topic: String): TopicInfo = describeOrNull(topic) ?: throw TopicNotFoundException(topic)

    suspend fun describeOrNull(topic: String): TopicInfo? =
        try {
            admin
                .describeTopics(listOf(topic))
                .allTopicNames()
                .await()
                .getValue(topic)
                .let { description ->
                    TopicInfo(
                        name = description.name(),
                        partitions =
                            description.partitions().map { partition ->
                                PartitionInfo(
                                    partition = partition.partition(),
                                    leader = partition.leader()?.id(),
                                    replicas = partition.replicas().map { it.id() },
                                    inSync = partition.isr().map { it.id() },
                                )
                            },
                    )
                }
        } catch (_: UnknownTopicOrPartitionException) {
            null
        }

    /** The newest offset in each of [partitions] — where a consumer reading from the end would start. */
    suspend fun endOffsets(partitions: Collection<TopicPartition>): Map<TopicPartition, Long> {
        if (partitions.isEmpty()) return emptyMap()
        return admin
            .listOffsets(partitions.associateWith { OffsetSpec.latest() })
            .all()
            .await()
            .mapValues { (_, offset) -> offset.offset() }
    }

    suspend fun groups(): Set<String> =
        admin
            .listGroups()
            .all()
            .await()
            .map { it.groupId() }
            .toSet()

    /** Where [group] has committed to, per partition. Empty for a group that has never committed. */
    suspend fun groupOffsets(group: String): Map<TopicPartition, Long> =
        admin
            .listConsumerGroupOffsets(group)
            .partitionsToOffsetAndMetadata()
            .await()
            .mapNotNull { (partition, offset) -> offset?.let { partition to it.offset() } }
            .toMap()

    /**
     * How far behind [group] is, per partition: the newest offset minus what the group committed.
     *
     * The number every alert is really about. A partition the group has never committed to counts
     * its whole length, because that is how much work is waiting.
     */
    suspend fun lag(group: String): Map<TopicPartition, Long> {
        val committed = groupOffsets(group)
        if (committed.isEmpty()) return emptyMap()
        val ends = endOffsets(committed.keys)
        return committed.mapValues { (partition, offset) -> (ends[partition] ?: offset) - offset }
    }

    /** Forgets [group] entirely, offsets included. Silent when there was no such group. */
    suspend fun deleteGroup(group: String) {
        try {
            admin.deleteConsumerGroups(listOf(group)).all().await()
        } catch (_: org.apache.kafka.common.errors.GroupIdNotFoundException) {
            // already gone
        }
    }

    override fun close() = admin.close()
}

/** Awaits a Kafka future without parking a thread on it. */
private suspend fun <T> KafkaFuture<T>.await(): T = toCompletionStage().await()

/**
 * An admin client for this cluster, which the caller owns and closes.
 *
 * ```kotlin
 * kafka.admin().use { it.ensureTopic("orders", partitions = 6) }
 * ```
 */
fun Kafka.admin(): KafkaAdmin = KafkaAdmin(Admin.create(config.clientProperties()))
