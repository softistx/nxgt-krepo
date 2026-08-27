package com.strange.amqp.codec

import kotlinx.serialization.json.Json

/**
 * What a body is serialized through unless the caller says otherwise.
 *
 * Lenient about unknown keys, because a message is written by whoever deployed last and read by
 * whoever deployed first: a consumer that throws on a field a newer publisher added is a consumer
 * that empties into a dead-letter queue during a rolling deploy. Strictness belongs on the way in,
 * where the publisher can still be fixed, not on the way out of a queue.
 */
val amqpJson: Json =
    Json {
        ignoreUnknownKeys = true
    }
