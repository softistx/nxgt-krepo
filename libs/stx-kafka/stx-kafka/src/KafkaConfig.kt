package com.softistx.kafka

import com.softistx.kafka.serde.kafkaJson
import kotlinx.serialization.json.Json

/**
 * Where the cluster is, and how this application talks to it.
 *
 * [bootstrap] is a *seed*, not the cluster: the client asks whoever answers for the real broker
 * list and talks to those from then on. Listing more than one is how a start survives a broker
 * being down, and nothing else about it matters.
 *
 * [json] is what every typed publisher and subscriber serializes through, so a service configures
 * its Kafka values once rather than once per topic. See [kafkaJson] for what the default is lenient
 * about and why.
 *
 * [properties] is the escape hatch, and it is spelled the way Kafka spells it — `security.protocol`,
 * `compression.type`, `max.poll.records`. This module models the settings it has an opinion about
 * and refuses to re-name the hundred it does not, so a line from Kafka's own documentation can be
 * pasted in and work.
 */
data class KafkaConfig(
    val bootstrap: String = "localhost:9092",
    val clientId: String? = null,
    val json: Json = kafkaJson,
    val properties: Map<String, String> = emptyMap(),
)
