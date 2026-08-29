package com.strange.amqp.message

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Delivery
import com.strange.amqp.codec.AmqpCodec
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * One delivery, decoded.
 *
 * [deliveryTag] is the acknowledgement handle and it belongs to *the channel it arrived on* — a tag
 * acknowledged on another channel is a `PRECONDITION_FAILED` that closes it. The consumer that
 * handed this over is the one that can ack it, which is why nothing here acks itself.
 *
 * [redelivered] is the broker saying this message has been delivered before — after a crash, a
 * `nack`, or a consumer that died holding it. It is not a promise the first attempt failed, only
 * that a first attempt happened, and it is the cheapest hint an idempotent handler gets.
 */
data class AmqpMessage<T>(
    val body: T,
    val exchange: String,
    val routingKey: String,
    val deliveryTag: Long,
    val redelivered: Boolean,
    val headers: MessageHeaders,
    val properties: BasicProperties,
) {
    /** The publisher's own message id, when it set one — what deduplication is usually keyed on. */
    val messageId: String? get() = properties.messageId

    val correlationId: String? get() = properties.correlationId

    val replyTo: String? get() = properties.replyTo

    val contentType: String? get() = properties.contentType

    val timestamp: Instant? get() = properties.timestamp?.toInstant()?.toKotlinInstant()

    /**
     * How many times this message has been dead-lettered, from the header the broker maintains.
     *
     * The `x-death` header is what a retry loop counts, and reading it by hand is three casts deep
     * into the client's own types.
     */
    val deathCount: Long
        get() {
            val deaths = headers["x-death"] as? List<*> ?: return 0
            val first = deaths.firstOrNull() as? Map<*, *> ?: return 0
            return (first["count"] as? Number)?.toLong() ?: 0
        }

    companion object {
        internal fun <T> from(
            delivery: Delivery,
            codec: AmqpCodec<T>,
        ): AmqpMessage<T> =
            AmqpMessage(
                body = codec.decode(delivery.body),
                exchange = delivery.envelope.exchange,
                routingKey = delivery.envelope.routingKey,
                deliveryTag = delivery.envelope.deliveryTag,
                redelivered = delivery.envelope.isRedeliver,
                headers = MessageHeaders(delivery.properties.headers.orEmpty()),
                properties = delivery.properties,
            )
    }
}
