package com.softistx.kafka.producer

import com.softistx.kafka.KafkaTestCluster
import com.softistx.kafka.admin.admin
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Serializable
import org.apache.kafka.common.TopicPartition

@Serializable
private data class OrderPlaced(
    val id: String,
    val total: Int = 0,
)

/**
 * The same publisher against the real cluster, which is the only thing that can answer the
 * questions that matter: does `acks = all` actually come back, is the wire form what another
 * service would read, and does a key really pin a record to a partition. The first of those is only
 * fully answered against a multi-broker cluster — see `KafkaTestCluster.replicationFactor`.
 */
class KafkaPublisherIntegrationTest :
    FeatureSpec({

        feature("a record on a real topic").config(enabled = KafkaTestCluster.available) {
            scenario("it is acknowledged by every in-sync replica and lands where it says") {
                KafkaTestCluster.withTopic { topic ->
                    KafkaTestCluster.kafka().publisher<OrderPlaced>().use { orders ->
                        val sent = orders.send(topic, OrderPlaced("o1", total = 42), key = "o1")

                        sent.topic shouldBe topic
                        sent.partition shouldBe 0
                        sent.offset shouldBe 0L

                        KafkaTestCluster.kafka().admin().use { admin ->
                            admin.endOffsets(listOf(TopicPartition(topic, 0))) shouldBe
                                mapOf(TopicPartition(topic, 0) to 1L)
                        }
                    }
                }
            }

            scenario("what is on the wire is the JSON another service would read") {
                KafkaTestCluster.withTopic { topic ->
                    KafkaTestCluster.kafka().publisher<OrderPlaced>().use { orders ->
                        orders.send(topic, OrderPlaced("o1", total = 42), key = "o1")
                    }

                    val raw = KafkaTestCluster.readRaw(topic, count = 1).single()

                    raw.key() shouldBe "o1"
                    raw.value() shouldContain """"id":"o1""""
                }
            }

            scenario("records sharing a key share a partition, which is what keeps them in order") {
                KafkaTestCluster.withTopic(partitions = 3) { topic ->
                    KafkaTestCluster.kafka().publisher<OrderPlaced>().use { orders ->
                        val partitions =
                            (1..6).map { orders.send(topic, OrderPlaced("o$it"), key = "same-key").partition }

                        partitions.distinct().size shouldBe 1
                    }
                }
            }
        }
    })
