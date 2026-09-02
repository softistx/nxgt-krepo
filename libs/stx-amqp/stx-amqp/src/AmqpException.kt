package com.softistx.amqp

/** What this module throws that the client does not. */
sealed class AmqpException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * A body that could not be turned into the type the caller asked for.
 *
 * Where it came from is part of the message because that is what a debugger needs: a queue's worth
 * of messages that no consumer can read is a different problem from one that cannot.
 */
class AmqpValueException(
    message: String,
    val exchange: String? = null,
    val routingKey: String? = null,
    val deliveryTag: Long? = null,
    cause: Throwable? = null,
) : AmqpException(
        buildString {
            append(message)
            routingKey?.let { append(" (routing key '").append(it).append('\'') }
            exchange?.let { append(if (routingKey == null) " (exchange '" else ", exchange '").append(it).append('\'') }
            deliveryTag?.let { append(if (routingKey == null && exchange == null) " (" else ", ").append("delivery ").append(it) }
            if (routingKey != null || exchange != null || deliveryTag != null) append(')')
        },
        cause,
    )

/**
 * The broker refused to store a published message.
 *
 * A `nack` is the broker saying it could not take responsibility — a full disk, a failed mirror —
 * and it is the one publish outcome that must not be mistaken for success.
 */
class AmqpNackException(
    val exchange: String,
    val routingKey: String,
) : AmqpException("the broker nacked the message published to '$exchange' with routing key '$routingKey'")

/**
 * A mandatory message that reached the broker and matched no queue.
 *
 * Without `mandatory` this is silence: an exchange with no binding for a routing key discards the
 * message and confirms it, which is the single most common way a working publisher delivers nothing.
 */
class AmqpUnroutableException(
    val exchange: String,
    val routingKey: String,
    val replyText: String,
) : AmqpException("nothing is bound to '$exchange' for routing key '$routingKey': $replyText")

/**
 * A publish that was still waiting for its confirm when the publisher closed.
 *
 * The message may or may not have reached the broker — that is the whole point of a confirm, and
 * closing is the caller saying they will not wait to find out. Reported rather than left hanging,
 * because a coroutine awaiting an answer nobody will ever give is the worst of the three outcomes.
 */
class AmqpClosedException(
    val exchange: String,
    val routingKey: String,
) : AmqpException("the publisher closed before the broker confirmed the message to '$exchange' with routing key '$routingKey'")
