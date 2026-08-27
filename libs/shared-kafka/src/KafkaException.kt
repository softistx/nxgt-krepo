package com.strange.kafka

/**
 * What this module throws. Kafka's own `org.apache.kafka.common.KafkaException` and its subclasses
 * still come through untouched — the client said something specific about the protocol, and
 * replacing it with a summary loses the part an operator needs.
 */
sealed class KafkaDataException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * A record could not be read as the type this consumer expects.
 *
 * [topic], [partition] and [offset] are filled in by the consumer, which is the only place that
 * knows them — a `Deserializer` is handed bytes and a topic name and nothing else. With them, the
 * record can be looked at; without them the message is just "something on this topic was wrong".
 */
class KafkaValueException(
    message: String,
    val topic: String? = null,
    val partition: Int? = null,
    val offset: Long? = null,
    cause: Throwable? = null,
) : KafkaDataException(
        buildString {
            append(message)
            if (topic != null) append(" at $topic-$partition offset $offset")
        },
        cause,
    )

/** The topic is not there, and this operation is not the one that creates it. */
class TopicNotFoundException(
    val topic: String,
) : KafkaDataException("No topic '$topic'")
