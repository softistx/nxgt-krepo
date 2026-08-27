package com.strange.amqp.topology

import kotlin.time.Duration

/**
 * How an exchange decides which queues a message goes to.
 *
 * The choice is about routing keys, not about speed: [Topic] and [Direct] cost the same, and a
 * [Topic] exchange with keys nobody wildcards is a [Direct] exchange that can be wildcarded later
 * without a migration. Which is why it is the default here.
 */
enum class ExchangeType(
    val value: String,
) {
    /** Routing key must match the binding key exactly. */
    Direct("direct"),

    /** Routing key is matched by pattern — `order.*` for one word, `order.#` for the rest. */
    Topic("topic"),

    /** Every bound queue, routing key ignored. */
    Fanout("fanout"),

    /** Matched on headers rather than the routing key. Rare, and slower than it looks. */
    Headers("headers"),
}

/**
 * Classic or quorum.
 *
 * [Quorum] replicates the queue across brokers by consensus and is what a message that must not be
 * lost belongs in; [Classic] lives on one broker and disappears with it. Quorum queues cost more
 * memory per message and cannot be exclusive or auto-delete — they are not the default here only
 * because a single-broker development machine gains nothing from consensus.
 */
enum class QueueType(
    val value: String,
) {
    Classic("classic"),
    Quorum("quorum"),
}

/**
 * Where a message goes when a queue is done with it.
 *
 * A message is dead-lettered when it is rejected without requeue, when it expires, or when the
 * queue is full — and this is the difference between "we lost it" and "it is over there". A retry
 * loop is two queues and one of these: the work queue dead-letters to a delay queue, and the delay
 * queue's TTL dead-letters it back.
 *
 * [routingKey] replaces the message's own on the way out. Leaving it null keeps the original, which
 * is what makes one dead-letter exchange able to serve queues that mean different things.
 */
data class DeadLetter(
    val exchange: String,
    val routingKey: String? = null,
)

/** An exchange, as it will be declared. */
data class Exchange(
    val name: String,
    val type: ExchangeType = ExchangeType.Topic,
    val durable: Boolean = true,
    val autoDelete: Boolean = false,
    val arguments: Map<String, Any> = emptyMap(),
)

/**
 * A queue, as it will be declared.
 *
 * **A declaration is not a schema migration.** The broker rejects a declare that disagrees with the
 * queue it already has, and closes the channel doing it, so changing [type] or [messageTtl] on a
 * queue that exists means deleting it — deliberately, once, by someone who knows what is in it —
 * rather than editing this and deploying.
 */
data class Queue(
    val name: String,
    val type: QueueType = QueueType.Classic,
    val durable: Boolean = true,
    val exclusive: Boolean = false,
    val autoDelete: Boolean = false,
    val deadLetter: DeadLetter? = null,
    val messageTtl: Duration? = null,
    val maxLength: Long? = null,
    val arguments: Map<String, Any> = emptyMap(),
) {
    init {
        require(type != QueueType.Quorum || (durable && !exclusive && !autoDelete)) {
            "a quorum queue is durable and neither exclusive nor auto-delete: '$name' is not"
        }
        require(messageTtl == null || messageTtl > Duration.ZERO) { "a message TTL is positive, not $messageTtl" }
        require(maxLength == null || maxLength > 0) { "a queue holds a positive number of messages, not $maxLength" }
    }

    /** The `x-` arguments this adds up to, with the caller's own last so they can override. */
    internal fun asArguments(): Map<String, Any> =
        buildMap {
            if (type != QueueType.Classic) put("x-queue-type", type.value)
            deadLetter?.let { dead ->
                put("x-dead-letter-exchange", dead.exchange)
                dead.routingKey?.let { put("x-dead-letter-routing-key", it) }
            }
            messageTtl?.let { put("x-message-ttl", it.inWholeMilliseconds) }
            maxLength?.let { put("x-max-length", it) }
            putAll(arguments)
        }
}

/** A queue attached to an exchange for a routing key. */
data class Binding(
    val queue: String,
    val exchange: String,
    val routingKey: String = "",
    val arguments: Map<String, Any> = emptyMap(),
)
