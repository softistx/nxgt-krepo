package com.softistx.amqp.consumer

/**
 * When a message is taken off the queue for good.
 *
 * The acknowledgement is what makes redelivery stop, so this is the dial between doing work twice
 * and not doing it at all.
 */
sealed interface AckStrategy {
    /**
     * Acknowledged after the handler returned — at least once, and the default.
     *
     * A handler that succeeds and then loses the connection sees its message again, so handlers
     * have to be idempotent.
     */
    data object AfterHandler : AckStrategy

    /**
     * Acknowledged by the broker as it hands the message over — at most once.
     *
     * Faster and unrecoverable: a crash between delivery and handling loses the message, and there
     * is no record of it anywhere. For metrics and heartbeats, not for work.
     */
    data object OnDelivery : AckStrategy

    /** Nothing is acknowledged for you. The caller calls [AmqpConsumer.ack] or [AmqpConsumer.nack]. */
    data object Manual : AckStrategy
}

/** What to do with a message whose body will not decode. */
enum class Undecodable {
    /**
     * Reject it without requeue, so it goes to the queue's dead-letter exchange — and keep going.
     *
     * A body this consumer cannot read will not become readable by being tried again, and one such
     * message must not stop the queue behind it. This is the default for that reason.
     */
    DeadLetter,

    /**
     * Requeue it and stop the consumer.
     *
     * For a consumer whose deployment is the suspect — a rollback puts the message back in front of
     * a version that can read it.
     */
    Fail,
}

/**
 * How a consumer reads.
 *
 * [prefetch] is the one that matters. It is how many messages the broker will hand over before it
 * gets an acknowledgement back, and it is the only backpressure in AMQP: without it the broker
 * pushes the entire queue at the first consumer that attaches, which is a consumer holding a
 * queue's worth of messages in memory and every other consumer of that queue holding none.
 *
 * [concurrency] is how many messages a handler may be working on at once. Above one, the queue's
 * order stops being the handling order — AMQP's FIFO promise is about delivery, not about when
 * handlers finish.
 *
 * [requeueOnFailure] is off for a reason. A message that is requeued after failing is delivered
 * again, fails again, and is requeued again: the fastest infinite loop in messaging, and it looks
 * like a busy consumer rather than a broken one. Off means a failure is dead-lettered, which is
 * where it can be looked at.
 */
data class ConsumerOptions(
    val prefetch: Int = 32,
    val ack: AckStrategy = AckStrategy.AfterHandler,
    val concurrency: Int = 1,
    val requeueOnFailure: Boolean = false,
    val undecodable: Undecodable = Undecodable.DeadLetter,
    val consumerTag: String = "",
    val exclusive: Boolean = false,
    val arguments: Map<String, Any> = emptyMap(),
) {
    init {
        require(prefetch >= 0) { "a prefetch is not negative: $prefetch" }
        require(concurrency > 0) { "a consumer handles a positive number of messages at once, not $concurrency" }
    }
}
