package com.strange.amqp.topology

import com.strange.amqp.Amqp
import java.io.IOException

/**
 * The queue's depth — the number every dashboard and every alert is actually about.
 *
 * Cheap: it comes back from a passive declare, not from reading the queue.
 */
suspend fun Amqp.messageCount(queue: String): Long = withChannel { channel -> channel.messageCount(queue) }

/** How many consumers are attached — zero on a queue that is filling is the alert worth having. */
suspend fun Amqp.consumerCount(queue: String): Long = withChannel { channel -> channel.consumerCount(queue) }

/**
 * Whether the queue is there.
 *
 * A passive declare is the only way to ask, and a *failed* one closes the channel it was asked on —
 * which is why this borrows a channel rather than using anyone else's. Asking on a shared channel is
 * how a "does it exist" check takes a publisher down with it.
 */
suspend fun Amqp.queueExists(queue: String): Boolean =
    withChannel { channel ->
        try {
            channel.queueDeclarePassive(queue)
            true
        } catch (_: IOException) {
            false
        }
    }

/** The same question for an exchange, with the same channel-closing caveat behind it. */
suspend fun Amqp.exchangeExists(exchange: String): Boolean =
    withChannel { channel ->
        try {
            channel.exchangeDeclarePassive(exchange)
            true
        } catch (_: IOException) {
            false
        }
    }

/** Throws away everything in the queue and answers how much that was. */
suspend fun Amqp.purgeQueue(queue: String): Int = withChannel { channel -> channel.queuePurge(queue).messageCount }

/**
 * Deletes the queue and everything in it.
 *
 * [ifUnused] and [ifEmpty] are the guards worth using in anything that is not a test: without them
 * this discards messages nobody has read yet, and the broker will not warn.
 */
suspend fun Amqp.deleteQueue(
    queue: String,
    ifUnused: Boolean = false,
    ifEmpty: Boolean = false,
) {
    withChannel { channel -> channel.queueDelete(queue, ifUnused, ifEmpty) }
}

/** Deletes the exchange. Bindings to it go with it; the queues do not. */
suspend fun Amqp.deleteExchange(
    exchange: String,
    ifUnused: Boolean = false,
) {
    withChannel { channel -> channel.exchangeDelete(exchange, ifUnused) }
}

/** Takes down everything in [topology] — queues first, then the exchanges they were bound to. */
suspend fun Amqp.delete(topology: Topology) {
    topology.queues.forEach { queue -> runCatching { deleteQueue(queue.name) } }
    topology.exchanges.forEach { exchange -> runCatching { deleteExchange(exchange.name) } }
}
