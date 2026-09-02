package com.softistx.kafka.spring

import com.softistx.kafka.Kafka
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * What this auto-configuration does and, mostly, does not do.
 *
 * No broker anywhere, and none needed — which is the point rather than a shortcut. This is the one
 * integration whose context is not holding a connection at all: a Kafka client connects when it is
 * constructed, so the connections belong to the publishers and subscribers the cluster hands out,
 * each closed by whoever asked for it.
 */
class KafkaWiringTest :
    StringSpec({

        "nothing is registered until an application asks" {
            runner().run { context -> context.getBeanNamesForType(Kafka::class.java).size shouldBe 0 }
        }

        "enabling it registers a cluster handle that has opened nothing" {
            runner()
                .withPropertyValues(
                    "stx.kafka.enabled=true",
                    "stx.kafka.bootstrap=localhost:9092",
                    "stx.kafka.client-id=orders",
                ).run { context ->
                    val cluster = context.getBean(Kafka::class.java)
                    cluster.bootstrap shouldBe "localhost:9092"
                    cluster.config.clientId shouldBe "orders"
                }
        }
    })

private fun runner() =
    ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KafkaIntegrationAutoConfiguration::class.java))
