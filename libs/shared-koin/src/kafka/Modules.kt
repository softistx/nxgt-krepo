package com.strange.koin.kafka

import com.strange.kafka.Kafka
import com.strange.kafka.KafkaConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The cluster configuration, for the container to hand out.
 *
 * ```kotlin
 * startKoin { modules(kafkaModule(KafkaConfig(bootstrap = System.getenv("KAFKA_BOOTSTRAP")))) }
 * ```
 *
 * No `onClose`, and nothing to close: [Kafka] is a description of a cluster and opens nothing. A
 * publisher or a subscriber built from it is what holds a connection, so that is what a module
 * should register with an `onClose` — one per publisher, since a publisher owns a producer.
 *
 * In an application that also serves HTTP: `install(KafkaCluster) { instance = get() }`.
 */
fun kafkaModule(config: KafkaConfig = KafkaConfig()): Module =
    module {
        single { Kafka(config) }
    }
