package com.softistx.amqp.consumer

import com.rabbitmq.client.Channel
import com.rabbitmq.client.DeliverCallback
import com.softistx.amqp.Amqp
import com.softistx.amqp.AmqpConfig
import com.softistx.amqp.AmqpTestBroker
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ExecutorService

/** A queue with [messages] bodies in it, consumed by [onDeliver] on its own channel. */
internal class DeliveryQueue(
    private val amqp: Amqp,
    messages: Int,
    prefetch: Int,
    onDeliver: DeliverCallback,
) {
    val queue = AmqpTestBroker.name("threads")
    private val channel: Channel = amqp.openChannel()

    init {
        channel.queueDeclare(queue, true, false, false, null)
        repeat(messages) { channel.basicPublish("", queue, null, "{}".toByteArray()) }
        channel.basicQos(prefetch)
        channel.basicConsume(queue, false, onDeliver, { _ -> })
    }

    fun close() {
        runCatching { channel.queueDelete(queue) }
        runCatching { channel.close() }
    }
}

/** A queue with one message whose consumer records the thread that delivered it on [thread]. */
internal fun sibling(
    amqp: Amqp,
    thread: CompletableDeferred<String>,
) = DeliveryQueue(amqp, messages = 1, prefetch = 1) { _, _ -> thread.complete(Thread.currentThread().name) }

/** A connection for one spec, on [executor] as its shared delivery pool when given. */
internal suspend fun <T> connected(
    executor: ExecutorService?,
    block: suspend (Amqp) -> T,
): T {
    val config =
        AmqpConfig(
            uri = AmqpTestBroker.uri,
            connectionName = "stx-amqp delivery threads",
            configure = { factory -> executor?.let { factory.setSharedExecutor(it) } },
        )
    return Amqp.connect(config).use { block(it) }
}
