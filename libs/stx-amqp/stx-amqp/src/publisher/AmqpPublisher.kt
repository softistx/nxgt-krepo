package com.softistx.amqp.publisher

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.softistx.amqp.Amqp
import com.softistx.amqp.AmqpClosedException
import com.softistx.amqp.AmqpNackException
import com.softistx.amqp.AmqpUnroutableException
import com.softistx.amqp.codec.AmqpCodec
import com.softistx.amqp.codec.jsonCodec
import com.softistx.amqp.message.MessageHeaders
import com.softistx.common.coroutines.Mailbox
import kotlinx.coroutines.CompletableDeferred
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

    private val settling = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
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
        settling.launch { settle() }
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
    override fun close() {
        runCatching { channel.close() }
        confirms.close()
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
                val entry = Pending(sequence, exchange, routingKey, id)
                if (!options.confirms) {
                    entry.confirmed()
                } else {
                    /* Registered before the bytes go out, so the confirm that answers them cannot
                       reach the settler first: one queue, and this end of it is already in. */
                    confirms.post(Confirm.Registered(entry))
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

    // ─── Settling, all of it on one coroutine ─────────────────────────────────

    /**
     * Applies what the broker says, in the order it says it.
     *
     * Sole owner of both maps below, which is why neither is synchronised and why [Pending.returned]
     * is an ordinary `var`.
     */
    private suspend fun settle() {
        /* Insertion order is ascending sequence order, so "everything up to this tag" is a walk
           from the front rather than a sorted structure. */
        val pending = LinkedHashMap<Long, Pending>()
        val bySequence = mutableMapOf<String, Long>()

        fun forget(entry: Pending) {
            pending.remove(entry.sequence)
            entry.messageId?.let(bySequence::remove)
        }

        fun take(
            tag: Long,
            multiple: Boolean,
        ): List<Pending> =
            when {
                multiple -> pending.keys.takeWhile { it <= tag }.mapNotNull { pending[it] }
                else -> listOfNotNull(pending[tag])
            }.onEach(::forget)

        try {
            confirms.consume { message ->
                when (message) {
                    is Confirm.Registered -> {
                        pending[message.entry.sequence] = message.entry
                        message.entry.messageId?.let { bySequence[it] = message.entry.sequence }
                    }

                    /* The broker sends a return before the confirm for that message, so marking it
                       here and failing it on the confirm needs no second signal. */
                    is Confirm.Returned -> {
                        bySequence[message.messageId]?.let { pending[it]?.returned = message.replyText }
                    }

                    is Confirm.Acked -> {
                        take(message.tag, message.multiple).forEach { it.confirmed() }
                    }

                    is Confirm.Nacked -> {
                        take(message.tag, message.multiple).forEach { it.nacked() }
                    }

                    is Confirm.Failed -> {
                        take(message.sequence, false).forEach { it.failed(message.cause) }
                    }
                }
            }
        } finally {
            pending.values.forEach { it.abandoned() }
        }
    }

    private companion object {
        const val PERSISTENT = 2
        const val TRANSIENT = 1
    }
}

/** A publish the broker has not spoken about yet. Touched only by the settler once registered. */
private class Pending(
    val sequence: Long,
    val exchange: String,
    val routingKey: String,
    val messageId: String?,
) {
    val deferred = CompletableDeferred<Published>()

    /** The broker's reason for sending it back, seen before the confirm that follows it. */
    var returned: String? = null

    fun confirmed() {
        when (val reason = returned) {
            null -> deferred.complete(Published(exchange, routingKey, messageId, sequence))
            else -> deferred.completeExceptionally(AmqpUnroutableException(exchange, routingKey, reason))
        }
    }

    fun nacked() {
        deferred.completeExceptionally(AmqpNackException(exchange, routingKey))
    }

    fun failed(cause: Throwable) {
        deferred.completeExceptionally(cause)
    }

    fun abandoned() {
        deferred.completeExceptionally(AmqpClosedException(exchange, routingKey))
    }
}

/** What the broker's threads, and the publish itself, tell the settler. */
private sealed interface Confirm {
    data class Registered(
        val entry: Pending,
    ) : Confirm

    data class Acked(
        val tag: Long,
        val multiple: Boolean,
    ) : Confirm

    data class Nacked(
        val tag: Long,
        val multiple: Boolean,
    ) : Confirm

    data class Returned(
        val messageId: String,
        val replyText: String,
    ) : Confirm

    data class Failed(
        val sequence: Long,
        val cause: Throwable,
    ) : Confirm
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
