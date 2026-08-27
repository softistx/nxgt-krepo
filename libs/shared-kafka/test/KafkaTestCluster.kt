package com.strange.kafka

import kotlinx.coroutines.Dispatchers
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
import kotlin.time.Duration.Companion.seconds

/**
 * The Kafka this workspace already runs, not one a test starts — `~/workspace/docker/apps/kafka`
 * is a three-broker KRaft cluster, and `KAFKA_TEST_BOOTSTRAP` points the specs somewhere else when
 * needed.
 *
 * The brokers advertise their container hostnames and publish no host ports, so reaching them from
 * the host needs those names resolvable:
 *
 * ```
 * # /etc/hosts
 * 172.22.0.115 kafka1
 * 172.22.0.116 kafka2
 * 172.22.0.117 kafka3
 * ```
 *
 * Without that the specs skip rather than fail — a machine that cannot see the cluster should
 * report skipped tests, not a red build.
 *
 * The cluster is shared, so the specs leave it as they found it: every topic they use is created
 * here with a name nothing else would choose, and deleted afterwards, and so is every consumer
 * group. Nothing here touches a topic or a group it did not create.
 */
internal object KafkaTestCluster {
    val bootstrap: String = System.getenv("KAFKA_TEST_BOOTSTRAP") ?: "kafka1:9092,kafka2:9094,kafka3:9096"

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
        runCatching { admin().use { it.describeCluster().nodes().get() } }.isSuccess
    }

    fun topicName(): String = "shared-kafka-test-${counter.incrementAndGet()}-${System.nanoTime()}"

    /**
     * A topic of its own, deleted when [block] returns.
     *
     * Three replicas because that is what this cluster is for: `min.insync.replicas` is 2, so a
     * single-replica topic would quietly not exercise `acks=all` at all.
     */
    suspend fun withTopic(
        partitions: Int = 1,
        replication: Short = 3,
        block: suspend (String) -> Unit,
    ) {
        val name = topicName()
        admin().use { admin ->
            withContext(Dispatchers.IO) {
                admin.createTopics(listOf(NewTopic(name, partitions, replication))).all().get()
            }
            try {
                block(name)
            } finally {
                withContext(Dispatchers.IO) {
                    runCatching { admin.deleteTopics(listOf(name)).all().get() }
                }
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

    /** Deletes a consumer group the spec created, ignoring one that never came into being. */
    suspend fun deleteGroup(group: String) {
        admin().use { admin ->
            withContext(Dispatchers.IO) {
                runCatching { admin.deleteConsumerGroups(listOf(group)).all().get() }
            }
        }
    }
}
