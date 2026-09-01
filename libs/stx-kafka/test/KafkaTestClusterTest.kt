package com.softistx.kafka

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The harness before anything is built on it: if `withTopic` does not really create and really
 * remove a topic, every spec after this one is either testing nothing or leaving rubbish on a
 * cluster it shares with the rest of the workspace.
 */
class KafkaTestClusterTest :
    FeatureSpec({

        feature("the cluster the specs run against").config(enabled = KafkaTestCluster.available) {
            // This used to demand three brokers, which was true of the workspace cluster and of
            // nothing else. The replication factor is derived now, so what is worth pinning is the
            // derivation: ask for what the cluster has, up to three, because asking for more than
            // it has is not a weaker test but a refused createTopics.
            scenario("however many brokers it has is what a topic gets, up to three") {
                KafkaTestCluster.admin().use { admin ->
                    val nodes = withContext(Dispatchers.IO) { admin.describeCluster().nodes().get() }

                    nodes.size shouldBeGreaterThanOrEqual 1
                    KafkaTestCluster.replicationFactor.toInt() shouldBe minOf(nodes.size, 3)
                }
            }

            scenario("a spec's topic exists while it runs and is gone afterwards") {
                lateinit var used: String

                KafkaTestCluster.withTopic { topic ->
                    used = topic
                    KafkaTestCluster.admin().use { admin ->
                        withContext(Dispatchers.IO) { admin.listTopics().names().get() } shouldContain topic
                    }
                }

                KafkaTestCluster.admin().use { admin ->
                    withContext(Dispatchers.IO) { admin.listTopics().names().get() } shouldNotContain used
                }
            }
        }
    })
