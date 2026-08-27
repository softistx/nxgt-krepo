package com.strange.amqp

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * A broker connection, and the clients built on it.
 *
 * ```kotlin
 * Amqp.connect(AmqpConfig(uri = "amqp://rabbit:5672/billing")).use { amqp ->
 *     amqp.publisher<OrderEvent>("orders").publish(OrderPlaced(id), routingKey = "order.placed")
 * }
 * ```
 *
 * **One connection is the right number.** AMQP multiplexes: a connection is one TCP socket carrying
 * any number of *channels*, and the reason to open a second connection is a second set of
 * credentials or a second vhost, not a second thread. A connection per publisher is how an
 * application ends up with a file descriptor problem and a broker whose connection list is
 * unreadable.
 *
 * **A channel, on the other hand, belongs to one user at a time.** `Channel` is not thread-safe and
 * its delivery tags are meaningless anywhere else, so every publisher and consumer here opens its
 * own and closes it, and one-shot work like declaring topology borrows one for the call. Sharing a
 * channel between two publishers is the bug that shows up as an acknowledgement applied to the
 * wrong message.
 *
 * Recovery is on by default: the client reconnects after a broker restart or a dropped socket and
 * re-declares the exchanges, queues and bindings it declared. What it cannot recover is a message
 * that was in flight, which is what publisher confirms and acknowledgements are for.
 */
class Amqp internal constructor(
    val connection: Connection,
    val config: AmqpConfig,
) : AutoCloseable {
    /** What the typed factories serialize through unless handed another. */
    val json: Json get() = config.json

    val isOpen: Boolean get() = connection.isOpen

    /** A channel the caller owns and closes — for a publisher or a consumer that keeps one. */
    fun openChannel(): Channel = connection.createChannel() ?: error("the broker refused a channel: the connection is out of them")

    /**
     * Borrows a channel for one piece of work.
     *
     * On [Dispatchers.IO] because every call in this client blocks — a declare is a round trip to
     * the broker and back, however small it looks.
     *
     * The close is deliberately not `use`. A channel the *broker* closed — which is what a failed
     * passive declare leaves behind — throws again when it is closed a second time, and the caller
     * would get that exception instead of the answer it came for.
     */
    suspend fun <T> withChannel(block: (Channel) -> T): T =
        withContext(Dispatchers.IO) {
            val channel = openChannel()
            try {
                block(channel)
            } finally {
                runCatching { channel.close() }
            }
        }

    /** Closes the connection, and with it every channel opened on it. */
    override fun close() {
        runCatching { connection.close() }
    }

    companion object {
        /**
         * Opens the connection, or throws whatever the client throws when it cannot.
         *
         * Suspending because it is a socket, a handshake and an authentication round trip, and none
         * of that belongs on the caller's dispatcher.
         */
        suspend fun connect(config: AmqpConfig = AmqpConfig()): Amqp =
            withContext(Dispatchers.IO) {
                val factory =
                    ConnectionFactory().apply {
                        setUri(config.uri)
                        requestedHeartbeat = config.heartbeat.inWholeSeconds.toInt()
                        connectionTimeout = config.connectionTimeout.inWholeMilliseconds.toInt()
                        isAutomaticRecoveryEnabled = config.recovery
                        isTopologyRecoveryEnabled = config.recovery
                        config.configure(this)
                    }
                /* Naming the connection costs nothing and is the difference between a management UI
                   listing five sockets and one listing five services. */
                val connection = config.connectionName?.let { factory.newConnection(it) } ?: factory.newConnection()
                Amqp(connection, config)
            }
    }
}
