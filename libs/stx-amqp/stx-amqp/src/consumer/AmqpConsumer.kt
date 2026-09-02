package com.softistx.amqp.consumer

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import com.softistx.amqp.Amqp
import com.softistx.amqp.AmqpValueException
import com.softistx.amqp.codec.AmqpCodec
import com.softistx.amqp.codec.jsonCodec
import com.softistx.amqp.message.AmqpMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
 * where other consumers can reach it instead of hoarding it in memory.
 *
 * **A delivery tag belongs to the channel it arrived on.** Acknowledging one on another channel is
 * a `PRECONDITION_FAILED` that closes it, which is why a consumer owns its channel and why nothing
 * else may borrow it.
 *
 * **Delivery is at least once by default.** The acknowledgement happens after the handler returned,
 * so a handler that succeeds and then loses the connection sees its message again. The alternative
 * — [AckStrategy.OnDelivery] — trades duplicates for messages that are silently never handled.
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
                            undecodable(delivery, failure)
                            return@DeliverCallback
                        }
                    /* Blocking the broker's dispatch thread *is* the backpressure: with prefetch
                       set, at most that many messages can be waiting here, and the broker gives the
                       rest to somebody else rather than to this consumer's heap. */
                    trySendBlocking(message).getOrThrow()
                }
            val cancelled = CancelCallback { close() }

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
                )

            awaitClose { runCatching { broker.basicCancel(tag) } }
        }

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
    ) = coroutineScope {
        val inFlight = Semaphore(options.concurrency)

        messages().collect { message ->
            if (options.concurrency == 1) {
                handle(message, handler, onFailure)
            } else {
                this@coroutineScope.launch { inFlight.withPermit { handle(message, handler, onFailure) } }
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
    override fun close() {
        runCatching { channel.close() }
    }

    // ─── Handling ─────────────────────────────────────────────────────────────

    private suspend fun handle(
        message: AmqpMessage<T>,
        handler: suspend (AmqpMessage<T>) -> Unit,
        onFailure: (AmqpMessage<T>, Throwable) -> Unit,
    ) {
        try {
            handler(message)
        } catch (cancellation: CancellationException) {
            /* The collector is going away, not the message: leave it unacknowledged so the broker
               gives it to whoever comes next. */
            throw cancellation
        } catch (failure: Throwable) {
            onFailure(message, failure)
            runCatching { nack(message, requeue = options.requeueOnFailure) }
            return
        }
        if (options.ack is AckStrategy.AfterHandler) ack(message)
    }

    private fun undecodable(
        delivery: Delivery,
        failure: AmqpValueException,
    ) {
        when (options.undecodable) {
            Undecodable.DeadLetter -> {
                if (!autoAck) runCatching { channel.basicReject(delivery.envelope.deliveryTag, false) }
            }

            Undecodable.Fail -> {
                if (!autoAck) runCatching { channel.basicNack(delivery.envelope.deliveryTag, false, true) }
                throw failure
            }
        }
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
