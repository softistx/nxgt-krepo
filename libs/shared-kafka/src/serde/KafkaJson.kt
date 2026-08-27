package com.strange.kafka.serde

import com.strange.common.serialization.lenientJson
import kotlinx.serialization.json.Json

/**
 * What a record's value is serialized through unless the caller says otherwise.
 *
 * The shared [lenientJson]: unknown keys are ignored, because a record is written by whoever
 * deployed last and read by whoever deployed first — a consumer that throws on a field a newer
 * producer added is a consumer that stops on a rolling deploy. Configured once on `KafkaConfig`,
 * so a service decides this for its Kafka values in one place rather than per topic.
 */
val kafkaJson: Json = lenientJson
