package com.strange.kafka

import kotlinx.serialization.json.Json

/**
 * A cluster, and the clients built on it.
 *
 * ```kotlin
 * val kafka = Kafka(KafkaConfig(bootstrap = "kafka1:9092,kafka2:9094,kafka3:9096"))
 * ```
 *
 * Deliberately not a `connect()`: nothing here opens anything. A Kafka client connects when it is
 * created and not before, so this is the configuration and the factories over it — the connections
 * belong to the publishers, subscribers and admin clients it hands out, each of which the caller
 * owns and closes. That is not an accident of this wrapper: a producer and a consumer have
 * different lifetimes, different threads and different failure modes, and pretending one object
 * owns them all is how a wrapper ends up closing a producer that another part of the application
 * was still using.
 *
 * Values are kotlinx.serialization first. `publisher<OrderEvent>()` and `subscriber<OrderEvent>()`
 * take the type alone and serialize it through this cluster's [json]; a value that is not JSON goes
 * through a `KafkaSerde` instead, which is what the typed factories hold underneath.
 */
class Kafka(
    val config: KafkaConfig,
) {
    /** What the typed factories serialize through unless handed another. */
    val json: Json get() = config.json

    val bootstrap: String get() = config.bootstrap
}
