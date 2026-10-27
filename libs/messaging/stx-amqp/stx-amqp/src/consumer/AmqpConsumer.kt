package com.softistx.amqp.consumer

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConsumerShutdownSignalCallback
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Recoverable
import com.softistx.amqp.Amqp
import com.softistx.amqp.AmqpBufferFullException
import com.softistx.amqp.AmqpValueException
import com.softistx.amqp.codec.AmqpCodec
import com.softistx.amqp.codec.jsonCodec
import com.softistx.amqp.message.AmqpMessage
import com.softistx.common.lifecycle.CloseGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Messages in, as a `Flow` or as a handler, with the acknowledgements looked after.
 *
 * ```kotlin
 * amqp.consumer<OrderEvent>("billing").use { billing ->
 *     billing.process { message -> charge(message.body) }
 * }
 * ```
 *
 * AMQP pushes rather than polls, so there is no loop here and nothing to keep alive — the broker
 * delivers, and the work is deciding what "handled" means. Four things about that are worth knowing:
 *
 * **Prefetch is the only backpressure there is.** The broker will hand over as many messages as it
 * is allowed to have unacknowledged, and the default in the protocol is *all of them*. So a
 * consumer sets [ConsumerOptions.prefetch], and a slow handler then leaves the rest of the queue
 * where other consumers can reach it instead of hoarding it in memory. It is capped at
 * [ConsumerOptions.MAX_PREFETCH], half the size of the buffer deliveries land in, so that buffer never
 * fills and the client's shared delivery thread is never parked waiting for the collector.
 *
 * **A delivery tag belongs to the channel it arrived on.** Acknowledging one on another channel is
 * a `PRECONDITION_FAILED` that closes it, which is why a consumer owns its channel and why nothing
 * else may borrow it.
 *
 * **Delivery is at least once by default.** The acknowledgement happens after the handler returned,
 * so a handler that succeeds and then loses the connection sees its message again. The alternative
 * — [AckStrategy.OnDelivery] — trades duplicates for messages that are silently never handled, and
 * has no prefetch: a collector that falls behind until the buffer is full fails the flow with
 * [AmqpBufferFullException] rather than blocking the client's delivery thread.
 *
 * **A failure is dead-lettered, not retried in place.** A message that is requeued after failing is
 * redelivered immediately, fails again, and is requeued again — an infinite loop that reads as a
 * busy consumer. Rejecting it sends it to the queue's dead-letter exchange, where it can be looked
 * at. A queue declared *without* one discards it instead, so declare one: see `DeadLetter`.
 */
class AmqpConsumer<T> internal constructor(
    private val channel: Channel,
    val queue: String,
    private val codec: AmqpCodec<T>,
    internal val options: ConsumerOptions,
) : AutoCloseable {
    private val autoAck = options.ack is AckStrategy.OnDelivery

    private val guard = CloseGuard()

    /**
     * The messages, as they arrive, until the collector stops.
     *
     * Nothing is acknowledged here — receiving a message is not handling it, and only the caller
     * knows when it has been. [ack] and [nack] are how a collector says so; [process] is the
     * version that says it for you.
     *
     * Cancelling the collection cancels the consumer with the broker, which hands whatever it had
     * not acknowledged to the next consumer of the queue.
     */
    fun messages(): Flow<AmqpMessage<T>> =
        callbackFlow {
            // `channel` inside here is the flow's own; the broker's is the one this consumer owns.
            val broker = this@AmqpConsumer.channel
            broker.basicQos(options.prefetch)

            val deliver =
                DeliverCallback { _, delivery ->
                    val message =
                        try {
                            AmqpMessage.from(delivery, codec)
                        } catch (failure: AmqpValueException) {
                            rejectUndecodable(channel = broker, options, delivery, failure) { close(it) }
                            return@DeliverCallback
                        }
                    /* Prefetch is capped at half the buffer's size ([ConsumerOptions.DELIVERY_BUFFER])
                       and a consumer holds no more unacknowledged messages than its prefetch, so
                       the buffer is never full here and the blocking send returns at once: the one
                       deliberate block on the client's delivery thread, bounded backpressure that
                       never waits for the collector. Uncapped, enough full buffers park every
                       thread of the connection's shared pool (`DeliveryThreadsTest`). Auto-ack has
                       no prefetch, so it never blocks: a full buffer fails the flow instead. */
                    if (autoAck) {
                        if (trySend(message).isFailure && !isClosedForSend) {
                            close(AmqpBufferFullException(queue, ConsumerOptions.DELIVERY_BUFFER))
                        }
                    } else {
                        trySendBlocking(message).getOrThrow()
                    }
                }
            val cancelled = CancelCallback { close() }

            /* A channel or connection that went away takes the consumer with it: nothing will be
               delivered again, so a collector left waiting would wait for ever. A close this side
               asked for ends the flow quietly; the broker's is the reason. A lost *connection*
               under automatic recovery is not an end: the client reconnects and re-registers
               this consumer, and the flow carries on. */
            val shutdown =
                ConsumerShutdownSignalCallback { _, signal ->
                    when (shutdownOutcome(signal, broker is Recoverable)) {
                        ShutdownOutcome.Continue -> Unit
                        ShutdownOutcome.End -> close()
                        ShutdownOutcome.Fail -> close(signal)
                    }
                }

            val tag =
                broker.basicConsume(
                    queue,
                    autoAck,
                    options.consumerTag,
                    false,
                    options.exclusive,
                    options.arguments,
                    deliver,
                    cancelled,
                    shutdown,
                )

            awaitClose { runCatching { broker.basicCancel(tag) } }
        }.buffer(ConsumerOptions.DELIVERY_BUFFER)

    /**
     * Hands every message to [handler] and acknowledges on the terms [ConsumerOptions.ack] set.
     *
     * A handler that throws rejects its message — to the dead-letter exchange, or back onto the
     * queue with [ConsumerOptions.requeueOnFailure] — and consumption continues, because one
     * message that cannot be handled must not stop the ones behind it. [onFailure] is where that
     * goes if it should be logged or counted; the dead-letter queue is the record either way.
     */
    suspend fun process(
        onFailure: (AmqpMessage<T>, Throwable) -> Unit = { _, _ -> },
        handler: suspend (AmqpMessage<T>) -> Unit,
    ) = dispatch { message -> handle(message, handler, onFailure) }

    /**
     * Runs [handle] for every message, one at a time or up to [ConsumerOptions.concurrency] at once.
     *
     * The permit is taken *before* the coroutine is launched: taken inside it, the collector would
     * keep launching while every permit was held, and the waiting coroutines would be as many as
     * the prefetch let the broker deliver.
     */
    internal suspend fun dispatch(handle: suspend (AmqpMessage<T>) -> Unit) =
        coroutineScope {
            val inFlight = Semaphore(options.concurrency)

            messages().collect { message ->
                if (options.concurrency == 1) {
                    handle(message)
                } else {
                    inFlight.acquire()
                    launch {
                        try {
                            handle(message)
                        } finally {
                            inFlight.release()
                        }
                    }
                }
            }
        }

    /** Takes the message off the queue for good. */
    suspend fun ack(
        message: AmqpMessage<T>,
        multiple: Boolean = false,
    ) {
        if (autoAck) return
        withContext(Dispatchers.IO) { channel.basicAck(message.deliveryTag, multiple) }
    }

    /**
     * Refuses the message.
     *
     * [requeue] puts it back at the head of the queue — for a failure this consumer knows is
     * temporary and *not* for one it will hit again immediately. Without it the message goes to the
     * queue's dead-letter exchange, or nowhere if the queue has none.
     */
    suspend fun nack(
        message: AmqpMessage<T>,
        requeue: Boolean = false,
        multiple: Boolean = false,
    ) {
        if (autoAck) return
        withContext(Dispatchers.IO) { channel.basicNack(message.deliveryTag, multiple, requeue) }
    }

    /** Cancels the consumer and closes its channel. The connection it came from stays open. */
    override fun close() =
        guard.once {
            runCatching { channel.close() }
        }
}

/**
 * A consumer of `T` from [queue], deserialized with kotlinx.serialization through this connection's
 * `Json`.
 *
 * ```kotlin
 * amqp.consumer<OrderEvent>("billing")
 * ```
 */
inline fun <reified T> Amqp.consumer(
    queue: String,
    options: ConsumerOptions = ConsumerOptions(),
    json: Json = this.json,
): AmqpConsumer<T> = consumer(queue, jsonCodec<T>(json), options)

/** The same consumer, named by its codec — for a body that is not JSON, or a `T` that cannot be reified. */
fun <T> Amqp.consumer(
    queue: String,
    codec: AmqpCodec<T>,
    options: ConsumerOptions = ConsumerOptions(),
): AmqpConsumer<T> = AmqpConsumer(openChannel(), queue, codec, options)
