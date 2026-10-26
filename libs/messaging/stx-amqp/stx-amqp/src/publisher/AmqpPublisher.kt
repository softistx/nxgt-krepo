package com.softistx.amqp.publisher

import com.rabbitmq.client.Channel
import com.softistx.amqp.Amqp
import com.softistx.amqp.AmqpUnroutableException
import com.softistx.amqp.codec.AmqpCodec
import com.softistx.amqp.codec.jsonCodec
import com.softistx.amqp.message.MessageHeaders
import com.softistx.common.coroutines.Mailbox
import com.softistx.common.lifecycle.CloseGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Messages out, one suspending call at a time.
 *
 * ```kotlin
 * amqp.publisher<OrderEvent>("orders").use { orders ->
 *     orders.publish(OrderPlaced(id), routingKey = "order.placed")
 * }
 * ```
 *
 * [publish] suspends until the broker has confirmed the message, and answers with where it went.
 * The client's own `basicPublish` returns as soon as the bytes are written to the socket: awaiting
 * the confirm is what turns *sent* into *stored*, and it is the whole reason this class exists.
 *
 * **The channel is the confirm scope, so a publisher owns one.** Confirms arrive as sequence
 * numbers meaningful only on the channel that produced them, and a channel is not thread-safe. One
 * publisher per exchange per configuration is the right number; concurrent callers of the *same*
 * publisher are fine, since the one thing that has to be atomic — taking the next sequence number
 * and publishing under it — is held under a lock.
 *
 * **Confirms arrive on the client's own thread, which cannot suspend**, so they are posted to a
 * [Mailbox] and applied by one coroutine that owns everything they touch. That is what lets the
 * bookkeeping below be a plain map with no synchronisation on it, and it is what makes a return and
 * the confirm that follows it arrive in that order — the order the broker sent them in — rather
 * than as two writes racing across two threads.
 *
 * **An unroutable message is a silent success unless asked otherwise.** With
 * [PublisherOptions.mandatory] the broker returns a message that matched no queue, and this turns
 * that return into an [AmqpUnroutableException] on the publish that caused it. Matching a return to
 * its publish is done by message id: one is generated when the caller did not set one, because the
 * alternative — the client's own return listener with no correlation at all — can say only that
 * *something* was returned.
 */
class AmqpPublisher<T> internal constructor(
    private val channel: Channel,
    private val exchange: String,
    private val codec: AmqpCodec<T>,
    private val options: PublisherOptions,
) : AutoCloseable {
    /** Everything that has to be applied in order, in one queue. Posted to from any thread. */
    private val confirms = Mailbox<Confirm>()

    /** Held across "take the next sequence number, then publish under it", which must not interleave. */
    private val publishing = Mutex()

    private val guard = CloseGuard()

    /** Bumped by the shutdown listener, so a publish can tell its sequence number outlived its channel. */
    private val epoch = AtomicLong()

    private val settling = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        /* A lost channel takes its sequence numbers with it: the client recovers it by default and
           restarts them at 1, so entries left in the map would be answered by the wrong confirms
           and the lost ones never. Fail them all at the loss, before anything new is registered. */
        channel.addShutdownListener { confirms.post(Confirm.Lost(epoch.incrementAndGet())) }
        if (options.confirms) {
            channel.confirmSelect()
            channel.addConfirmListener(
                { tag, multiple -> confirms.post(Confirm.Acked(tag, multiple)) },
                { tag, multiple -> confirms.post(Confirm.Nacked(tag, multiple)) },
            )
        }
        if (options.mandatory) {
            channel.addReturnListener { returned ->
                returned.properties.messageId?.let { confirms.post(Confirm.Returned(it, returned.replyText)) }
            }
        }
        settling.launch { confirms.settle() }
    }

    /**
     * Publishes one message and waits for the broker to confirm it.
     *
     * [routingKey] is what the exchange routes on: a binding key for a direct exchange, a pattern
     * for a topic one, ignored by a fanout. Publishing to the default exchange (`""`) with a queue's
     * name as the routing key delivers straight to that queue, which is the one case where a
     * routing key is a destination rather than a subject.
     */
    suspend fun publish(
        value: T,
        routingKey: String = "",
        headers: MessageHeaders = MessageHeaders.EMPTY,
        messageId: String? = null,
        correlationId: String? = null,
        replyTo: String? = null,
        expiration: Duration? = null,
        priority: Int? = null,
        timestamp: Instant? = null,
    ): Published =
        enqueue(value, routingKey, headers, messageId, correlationId, replyTo, expiration, priority, timestamp)
            .deferred
            .await()

    /**
     * Publishes all of them in order, then waits for the confirms together.
     *
     * The order is the point: messages published on one channel to one queue arrive in the order
     * they were published, and one coroutine per message would give that up for nothing — the
     * broker confirms them in a batch either way.
     */
    suspend fun publishAll(
        values: Collection<T>,
        routingKey: String = "",
        headers: MessageHeaders = MessageHeaders.EMPTY,
    ): List<Published> = values.map { value -> enqueue(value, routingKey, headers) }.map { it.deferred }.awaitAll()

    /**
     * Closes the channel, then the mailbox. The connection it came from stays open.
     *
     * The mailbox closes *gracefully*, so whatever the broker already said is applied before the
     * settler stops — and whatever it never got round to saying is failed rather than left as a
     * coroutine awaiting an answer that is not coming.
     */
    override fun close() =
        guard.once {
            runCatching { channel.close() }
            confirms.close()
        }

    private suspend fun enqueue(
        value: T,
        routingKey: String,
        headers: MessageHeaders,
        messageId: String? = null,
        correlationId: String? = null,
        replyTo: String? = null,
        expiration: Duration? = null,
        priority: Int? = null,
        timestamp: Instant? = null,
    ): Pending {
        val id = messageId ?: if (options.mandatory) UUID.randomUUID().toString() else null
        val properties =
            publishProperties(codec, options, headers, id, correlationId, replyTo, expiration, priority, timestamp)
        val body = codec.encode(value)

        return withContext(Dispatchers.IO) {
            publishing.withLock {
                val born = epoch.get()
                val sequence = if (options.confirms) channel.nextPublishSeqNo else 0L
                val entry = Pending(sequence, exchange, routingKey, id)
                if (!options.confirms) {
                    entry.confirmed()
                } else {
                    /* Registered before the bytes go out, so the confirm that answers them cannot
                       reach the settler first: one queue, and this end of it is already in. */
                    confirms.post(Confirm.Registered(entry, born))
                }
                try {
                    channel.basicPublish(exchange, routingKey, options.mandatory, properties, body)
                } catch (failure: Throwable) {
                    if (options.confirms) {
                        confirms.post(Confirm.Failed(sequence, failure))
                    }
                    entry.deferred.completeExceptionally(failure)
                }
                entry
            }
        }
    }
}

/**
 * A publisher of `T` to [exchange], serialized with kotlinx.serialization through this connection's
 * `Json`.
 *
 * ```kotlin
 * amqp.publisher<OrderEvent>("orders")
 * ```
 */
inline fun <reified T> Amqp.publisher(
    exchange: String = "",
    options: PublisherOptions = PublisherOptions(),
    json: Json = this.json,
): AmqpPublisher<T> = publisher(exchange, jsonCodec<T>(json), options)

/** The same publisher, named by its codec — for a body that is not JSON, or a `T` that cannot be reified. */
fun <T> Amqp.publisher(
    exchange: String = "",
    codec: AmqpCodec<T>,
    options: PublisherOptions = PublisherOptions(),
): AmqpPublisher<T> = AmqpPublisher(openChannel(), exchange, codec, options)
