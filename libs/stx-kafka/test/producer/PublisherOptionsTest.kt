package com.strange.kafka.producer

import com.strange.kafka.KafkaConfig
import com.strange.kafka.clientProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.apache.kafka.clients.producer.ProducerConfig

/**
 * These options become a property map that Kafka validates at client construction, where a wrong
 * value is a `ConfigException` from inside a constructor. Everything here is about catching that
 * one step earlier, in a form the compiler or a clear message can reject.
 */
class PublisherOptionsTest :
    FeatureSpec({

        fun properties(options: PublisherOptions) = options.asProperties().toMap()

        feature("durability") {
            scenario("each level is the string Kafka expects, which is not the name it has here") {
                properties(PublisherOptions())[ProducerConfig.ACKS_CONFIG] shouldBe "all"
                properties(PublisherOptions(acks = Acks.Leader, idempotent = false))[ProducerConfig.ACKS_CONFIG] shouldBe "1"
                properties(PublisherOptions(acks = Acks.None, idempotent = false))[ProducerConfig.ACKS_CONFIG] shouldBe "0"
            }

            scenario("an idempotent producer with weaker acks is refused here, not by the client") {
                val failure = shouldThrow<IllegalArgumentException> { PublisherOptions(acks = Acks.Leader) }

                failure.message shouldContain "idempotent producer requires Acks.All"
            }
        }

        feature("compression") {
            scenario("the enum carries Kafka's own spelling") {
                properties(PublisherOptions())[ProducerConfig.COMPRESSION_TYPE_CONFIG] shouldBe "none"
                properties(PublisherOptions(compression = Compression.Zstd))[ProducerConfig.COMPRESSION_TYPE_CONFIG] shouldBe "zstd"
            }
        }

        feature("what wins when two layers disagree") {
            scenario("the deployment's cluster properties override what this module chose") {
                val config = KafkaConfig("localhost:9092", properties = mapOf(ProducerConfig.ACKS_CONFIG to "1"))

                val resolved = config.clientProperties(*PublisherOptions().asProperties())

                resolved[ProducerConfig.ACKS_CONFIG] shouldBe "1"
            }

            scenario("and a call site's own properties override the deployment's") {
                val config = KafkaConfig("localhost:9092", properties = mapOf(ProducerConfig.LINGER_MS_CONFIG to "10"))
                val options = PublisherOptions(properties = mapOf(ProducerConfig.LINGER_MS_CONFIG to "25"))

                val resolved = config.clientProperties(*options.asProperties()).apply { putAll(options.properties) }

                resolved[ProducerConfig.LINGER_MS_CONFIG] shouldBe "25"
            }
        }
    })
