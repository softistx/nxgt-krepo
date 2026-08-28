package com.strange.kafka

import com.strange.testing.containers.kafkaContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The Kafka the integration specs talk to: a single-broker container started for this run, unless
 * `KAFKA_TEST_BOOTSTRAP` names a cluster that is already up.
 *
 * **One broker is not free, and the cost is [replicationFactor].** The workspace cluster at
 * `~/workspace/docker/apps/kafka` is three brokers with `min.insync.replicas = 2`, so a topic there
 * has three replicas and `acks = all` genuinely waits for a quorum. A container can only give one
 * replica, so there `acks = all` waits for one broker: the ack path is exercised, the quorum is not.
 * Every spec asks for [replicationFactor] rather than a hard three, so both run — and the one that
 * checks the replication factor checks the number it actually asked for.
 *
 * Three brokers in containers would be faithful, and would cost roughly 3 GiB and half a minute per
 * run. Point `KAFKA_TEST_BOOTSTRAP` at a real cluster to exercise the quorum:
 *
 * ```
 * # /etc/hosts — the workspace brokers advertise container hostnames and publish no host ports
 * 172.22.0.115 kafka1
 * 172.22.0.116 kafka2
 * 172.22.0.117 kafka3
 * ```
 *
 * ```bash
 * KAFKA_TEST_BOOTSTRAP="kafka1:9092,kafka2:9094,kafka3:9096" ./kotlin test -m shared-kafka
 * ```
 *
 * A reused cluster is shared, so the specs leave it as they found it: every topic they use is
 * created here with a name nothing else would choose, and deleted afterwards, and so is every
 * consumer group. Nothing here touches a topic or a group it did not create.
 */
internal object KafkaTestCluster {
    private val cluster = kafkaContainer()

    val bootstrap: String get() = requireNotNull(cluster.endpoint) { cluster.describe() }

    /**
     * As many replicas as this cluster can actually give, capped at three.
     *
     * Asked of the cluster rather than assumed, because the answer differs between the container and
     * the workspace's three brokers, and a topic asking for more replicas than there are brokers is
     * refused outright.
     */
    val replicationFactor: Short by lazy {
        val brokers =
            runCatching {
                admin().use {
                    it
                        .describeCluster()
                        .nodes()
                        .get()
                        .size
                }
            }.getOrDefault(1)
        minOf(brokers, 3).toShort()
    }

    /** The cluster handle the specs build their clients from. */
    fun kafka(properties: Map<String, String> = emptyMap()): Kafka = Kafka(KafkaConfig(bootstrap, properties = properties))

    private val counter = AtomicInteger()

    /** Fails fast rather than retrying into the spec's timeout when the cluster is not there. */
    fun admin(): Admin =
        Admin.create(
            mapOf(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG to 2_000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG to 4_000,
                AdminClientConfig.RETRIES_CONFIG to 0,
            ),
        )

    val available: Boolean by lazy {
        cluster.available && runCatching { admin().use { it.describeCluster().nodes().get() } }.isSuccess
    }

    fun topicName(): String = "shared-kafka-test-${counter.incrementAndGet()}-${System.nanoTime()}"

    /**
     * A topic of its own, deleted — and *gone* — when [block] returns.
     *
     * The second half matters as much as the first. Deletion is asynchronous, so returning as soon
     * as the controller accepts it leaves the next spec sharing a cluster with a topic this one
     * believes it removed; [awaitGone] is what makes the promise in this sentence true.
     *
     * [replicationFactor] replicas rather than a hard three: against the workspace's cluster that is
     * three and `acks = all` waits for a quorum, and against a one-broker container it is one and
     * waits for that broker. Asking for three on a cluster that has one is not a weaker test, it is
     * a refused `createTopics`.
     */
    suspend fun withTopic(
        partitions: Int = 1,
        replication: Short = replicationFactor,
        block: suspend (String) -> Unit,
    ) {
        val name = topicName()
        admin().use { admin ->
            withContext(Dispatchers.IO) {
                admin.createTopics(listOf(NewTopic(name, partitions, replication))).all().get()
            }
            awaitTopic(admin, name)
            try {
                block(name)
            } finally {
                withContext(Dispatchers.IO) {
                    runCatching { admin.deleteTopics(listOf(name)).all().get() }
                }
                // `deleteTopics` returns when the controller accepted it, not when every broker has
                // caught up, so without this the topic can still be listed after `withTopic` says it
                // is gone. Swallowed like the delete above: a cleanup that cannot finish must not
                // replace the failure the block was reporting.
                runCatching { awaitGone(admin, name) }
            }
        }
    }

    /**
     * Reads [count] records off [topic] with no deserialization of our own — the bytes as another
     * service would see them, which is the only way a spec can check the wire form rather than
     * checking this module against itself.
     */
    suspend fun readRaw(
        topic: String,
        count: Int,
        timeout: Duration = 10.seconds,
    ): List<ConsumerRecord<String, String>> =
        withContext(Dispatchers.IO) {
            KafkaConsumer(
                mapOf(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
                    ConsumerConfig.GROUP_ID_CONFIG to "shared-kafka-test-raw-${counter.incrementAndGet()}",
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ),
                StringDeserializer(),
                StringDeserializer(),
            ).use { consumer ->
                consumer.subscribe(listOf(topic))
                val records = mutableListOf<ConsumerRecord<String, String>>()
                val deadline = System.nanoTime() + timeout.inWholeNanoseconds
                while (records.size < count && System.nanoTime() < deadline) {
                    consumer.poll(java.time.Duration.ofMillis(500)).forEach { records += it }
                }
                records
            }
        }

    /**
     * Waits until every partition of [topic] has a leader.
     *
     * `createTopics` returns when the controller has accepted the creation, which is not when the
     * broker this client talks to next can answer about it. Asking for offsets in that window fails
     * with `UnknownTopicOrPartition`, and it is a race rather than a certainty — it showed up once
     * in a full-suite run and not at all when the module ran on its own.
     */
    suspend fun awaitTopic(
        admin: Admin,
        topic: String,
        timeout: Duration = 10.seconds,
    ) {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            val ready =
                withContext(Dispatchers.IO) {
                    runCatching {
                        admin
                            .describeTopics(listOf(topic))
                            .allTopicNames()
                            .get()[topic]
                            ?.partitions()
                            ?.all { it.leader() != null } == true
                    }.getOrDefault(false)
                }
            if (ready) return
            delay(50.milliseconds)
        }
        error("$topic never became visible on $bootstrap")
    }

    /**
     * Waits until [topic] is gone from the cluster's metadata.
     *
     * Deletion is the mirror of the race [awaitTopic] answers: `deleteTopics` returns when the
     * controller has accepted it, and the broker this client asks next can still list the topic for
     * a moment afterwards. It only ever showed up under the load of a full-suite run, with five
     * backends up at once, which is exactly when a spec that asserts it immediately is wrong.
     */
    suspend fun awaitGone(
        admin: Admin,
        topic: String,
        timeout: Duration = 10.seconds,
    ) {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            val gone =
                withContext(Dispatchers.IO) {
                    runCatching {
                        !admin
                            .listTopics()
                            .names()
                            .get()
                            .contains(topic)
                    }.getOrDefault(false)
                }
            if (gone) return
            delay(50.milliseconds)
        }
        error("$topic was still on $bootstrap after it was deleted")
    }

    /** Deletes a consumer group the spec created, ignoring one that never came into being. */
    suspend fun deleteGroup(group: String) {
        admin().use { admin ->
            withContext(Dispatchers.IO) {
                runCatching { admin.deleteConsumerGroups(listOf(group)).all().get() }
            }
        }
    }
}
