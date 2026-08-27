package com.strange.kafka.admin

import com.strange.kafka.KafkaTestCluster
import com.strange.kafka.TopicNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.kafka.common.TopicPartition

class KafkaAdminTest :
    FeatureSpec({

        feature("a topic's life").config(enabled = KafkaTestCluster.available) {
            scenario("creating it says so, creating it again does not, and deleting it takes it away") {
                KafkaTestCluster.kafka().admin().use { admin ->
                    val topic = KafkaTestCluster.topicName()

                    admin.ensureTopic(topic, partitions = 2) shouldBe true
                    admin.ensureTopic(topic, partitions = 2) shouldBe false
                    admin.exists(topic) shouldBe true
                    admin.topics() shouldContain topic

                    admin.deleteTopic(topic)

                    admin.exists(topic) shouldBe false
                    admin.topics() shouldNotContain topic
                }
            }

            scenario("deleting one that was never there is not an error") {
                KafkaTestCluster.kafka().admin().use { admin ->
                    admin.deleteTopic(KafkaTestCluster.topicName())
                }
            }
        }

        feature("describing a topic").config(enabled = KafkaTestCluster.available) {
            scenario("it reports what the cluster has, replicas and in-sync set included") {
                KafkaTestCluster.kafka().admin().use { admin ->
                    KafkaTestCluster.withTopic(partitions = 3) { topic ->
                        val info = admin.describe(topic)

                        info.name shouldBe topic
                        info.partitionCount shouldBe 3
                        info.replicationFactor shouldBe 3

                        /* This cluster runs min.insync.replicas 2, so a healthy topic has its
                           whole replica set caught up — anything less and acks=all is failing. */
                        info.underReplicated shouldBe emptyList()
                        info.partitions.first().leader shouldNotBe null
                    }
                }
            }

            scenario("a topic that is not there is null, or a failure that names it") {
                KafkaTestCluster.kafka().admin().use { admin ->
                    val absent = KafkaTestCluster.topicName()

                    admin.describeOrNull(absent).shouldBeNull()
                    shouldThrow<TopicNotFoundException> { admin.describe(absent) }.topic shouldBe absent
                }
            }
        }

        feature("offsets and lag").config(enabled = KafkaTestCluster.available) {
            scenario("a fresh topic ends at zero, and a group nobody has run has no lag to report") {
                KafkaTestCluster.kafka().admin().use { admin ->
                    KafkaTestCluster.withTopic { topic ->
                        admin.endOffsets(listOf(TopicPartition(topic, 0))) shouldBe mapOf(TopicPartition(topic, 0) to 0L)
                        admin.lag("shared-kafka-test-group-that-never-ran").shouldBeEmpty()
                    }
                }
            }
        }
    })
