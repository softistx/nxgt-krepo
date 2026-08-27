package com.strange.kafka.serde

import kotlinx.serialization.json.Json

/**
 * The `Json` every typed producer and consumer serializes through unless the cluster was given
 * another.
 *
 * Lenient about unknown keys because of what a Kafka record is: a message from another service,
 * often an older or newer deploy of it. A producer that adds a field must not break every consumer
 * that has not been rebuilt yet — that is the whole reason a log is decoupling anything. A caller
 * who wants that skew to be loud passes a strict `Json` in `KafkaConfig`, and gets a
 * `KafkaValueException` naming the record it could not read.
 */
val kafkaJson: Json = Json { ignoreUnknownKeys = true }
