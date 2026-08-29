package com.strange.amqp.codec

import com.strange.common.serialization.lenientJson
import kotlinx.serialization.json.Json

/**
 * What a message body is serialized through unless the caller says otherwise.
 *
 * The shared [lenientJson]: unknown keys are ignored, because a message is written by whoever
 * deployed last and read by whoever deployed first — a consumer that throws on a field a newer
 * publisher added is a consumer that empties into its dead-letter queue during a rolling deploy.
 * Configured once on `AmqpConfig`, so a service decides this for its bodies in one place rather
 * than per queue.
 */
val amqpJson: Json = lenientJson
