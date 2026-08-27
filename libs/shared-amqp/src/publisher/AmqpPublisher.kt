package com.strange.amqp.publisher

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.strange.amqp.Amqp
import com.strange.amqp.AmqpNackException
import com.strange.amqp.AmqpUnroutableException
import com.strange.amqp.codec.AmqpCodec
import com.strange.amqp.codec.jsonCodec
import com.strange.amqp.message.MessageHeaders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
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
    /** Publishes in flight, by the sequence number the broker will confirm them on. */
    private val pending = ConcurrentSkipListMap<Long, Pending>()

    /** Publishes whose return would otherwise be unattributable, by message id. */
    private val byMessageId = ConcurrentHashMap<String, Long>()

    /** Held across "take the next sequence number, then publish under it", which must not interleave. */
    private val publishing = Mutex()

    init {
        if (options.confirms) {
            channel.confirmSelect()
            channel.addConfirmListener(
                { tag, multiple -> settle(tag, multiple) { it.complete() } },
                { tag, multiple -> settle(tag, multiple) { it.nacked() } },
            )
        }
        if (options.mandatory) {
            channel.addReturnListener { returned ->
                /* The broker sends the return before the confirm, so marking here and failing on
                   the confirm below is enough — no waiting for a second signal that may not come. */
                returned.properties.messageId
                    ?.let { byMessageId[it] }
                    ?.let { sequence -> pending[sequence]?.returned = returned.replyText }
            }
        }
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

    /** Closes the channel. The connection it came from stays open. */
    override fun close() {
        runCatching { channel.close() }
    }

    // ─── Publishing ───────────────────────────────────────────────────────────

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
            AMQP.BasicProperties
                .Builder()
                .contentType(codec.contentType)
                .deliveryMode(if (options.persistent) PERSISTENT else TRANSIENT)
                .headers(headers.asPublished().ifEmpty { null })
                .messageId(id)
                .correlationId(correlationId)
                .replyTo(replyTo)
                .expiration(expiration?.inWholeMilliseconds?.toString())
                .priority(priority)
                .appId(options.appId)
                .timestamp(timestamp?.let { java.util.Date(it.toEpochMilliseconds()) })
                .build()
        val body = codec.encode(value)

        return withContext(Dispatchers.IO) {
            publishing.withLock {
                val sequence = if (options.confirms) channel.nextPublishSeqNo else 0L
                val entry = Pending(sequence, exchange, routingKey, id, confirmed = !options.confirms)
                if (options.confirms) {
                    pending[sequence] = entry
                    id?.let { byMessageId[it] = sequence }
                }
                try {
                    channel.basicPublish(exchange, routingKey, options.mandatory, properties, body)
                } catch (failure: Throwable) {
                    forget(entry)
                    entry.deferred.completeExceptionally(failure)
                }
                entry
            }
        }
    }

    /** Completes everything the broker has just spoken for — one tag, or every tag up to it. */
    private fun settle(
        tag: Long,
        multiple: Boolean,
        outcome: (Pending) -> Unit,
    ) {
        val settled = if (multiple) pending.headMap(tag, true).values.toList() else listOfNotNull(pending[tag])
        settled.forEach { entry ->
            forget(entry)
            outcome(entry)
        }
    }

    private fun forget(entry: Pending) {
        pending.remove(entry.sequence)
        entry.messageId?.let { byMessageId.remove(it) }
    }

    private inner class Pending(
        val sequence: Long,
        val exchange: String,
        val routingKey: String,
        val messageId: String?,
        confirmed: Boolean,
    ) {
        val deferred = CompletableDeferred<Published>()

        /** The broker's reason, set by the return listener before the confirm arrives. */
        @Volatile
        var returned: String? = null

        init {
            if (confirmed) complete()
        }

        fun complete() {
            when (val reason = returned) {
                null -> deferred.complete(Published(exchange, routingKey, messageId, sequence))
                else -> deferred.completeExceptionally(AmqpUnroutableException(exchange, routingKey, reason))
            }
        }

        fun nacked() {
            deferred.completeExceptionally(AmqpNackException(exchange, routingKey))
        }

        suspend fun await(): Published = deferred.await()
    }

    private companion object {
        const val PERSISTENT = 2
        const val TRANSIENT = 1
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
