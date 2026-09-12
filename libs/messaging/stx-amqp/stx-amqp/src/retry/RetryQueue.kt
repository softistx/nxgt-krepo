package com.softistx.amqp.retry

import com.softistx.amqp.Amqp
import com.softistx.amqp.codec.AmqpCodec
import com.softistx.amqp.codec.jsonCodec
import com.softistx.amqp.consumer.AmqpConsumer
import com.softistx.amqp.message.AmqpMessage
import com.softistx.amqp.message.MessageHeaders
import com.softistx.amqp.publisher.AmqpPublisher
import com.softistx.amqp.publisher.PublisherOptions
import com.softistx.amqp.publisher.publisher
import com.softistx.amqp.topology.DeadLetter
import com.softistx.amqp.topology.Topology
import com.softistx.amqp.topology.declare
import com.softistx.amqp.topology.delete
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json

/**
 * The retry path for a queue: one delay queue per attempt, and somewhere to park what is left.
 *
 * ```kotlin
 * amqp.retryQueue<OrderEvent>("billing").use { retries ->
 *     amqp.consumer<OrderEvent>("billing").use { billing ->
 *         billing.processWithRetry(retries) { message -> charge(message.body) }
 *     }
 * }
 * ```
 *
 * **AMQP has no delayed delivery, so a delay is a queue nobody consumes.** A message published to
 * `billing.retry.0` sits there until its TTL expires, at which point the broker dead-letters it —
 * and the dead-letter route is back to `billing`. That is the whole mechanism, and it is why the
 * delays are per queue rather than per message: a TTL only expires messages at the *head*, so one
 * queue with mixed TTLs would hold a short delay behind a long one.
 *
 * **The attempt count travels with the message**, in `x-attempt`, because the message that comes
 * back is a copy: this republishes rather than rejecting, so that the broker's own `x-death`
 * bookkeeping stays a record of what happened rather than the thing the loop depends on.
 *
 * **The work queue is not declared here.** It is the caller's, usually with its own bindings and
 * arguments, and re-declaring it with different ones is a channel-closing error. Only the delay
 * queues and the parking queue belong to this.
 *
 * What lands in [parked] has failed every attempt. Nothing takes it from there — that is the point
 * of the name.
 */
class RetryQueue<T> internal constructor(
    val queue: String,
    val policy: RetryPolicy,
    val delayQueues: List<String>,
    val parked: String,
    val topology: Topology,
    private val publisher: AmqpPublisher<T>,
) : AutoCloseable {
    /**
     * Sends [message] round the retry path, or parks it when its attempts are used up.
     *
     * Answers whether it will be tried again. The failure is carried as its type only: a parked
     * message needs to say what went wrong, and an exception's message is where the body it was
     * handed ends up quoted.
     */
    suspend fun retry(
        message: AmqpMessage<T>,
        failure: Throwable? = null,
    ): Boolean {
        val attempt = message.headers.int(ATTEMPT) ?: 0
        val willRetry = attempt < policy.attempts
        val headers =
            message.headers +
                buildMap {
                    put(ATTEMPT, attempt + 1)
                    put(ORIGIN, queue)
                    failure?.let { put(REASON, it::class.qualifiedName ?: it::class.java.name) }
                }

        publisher.publish(
            message.body,
            routingKey = if (willRetry) delayQueues[attempt] else parked,
            headers = MessageHeaders(headers.values),
            messageId = message.messageId,
            correlationId = message.correlationId,
        )
        return willRetry
    }

    /** Closes the publisher this uses. The queues stay where they are. */
    override fun close() = publisher.close()

    companion object {
        /** Which attempt this delivery is: absent on the first, one higher on every copy after it. */
        const val ATTEMPT = "x-attempt"

        /** The queue the message was first handled by, so a parked message says where it came from. */
        const val ORIGIN = "x-retry-origin"

        /** The type of the failure that caused the retry — not its message, which quotes bodies. */
        const val REASON = "x-retry-reason"
    }
}

/**
 * Declares the delay queues and the parking queue for [queue], and hands back the router over them.
 *
 * Names are derived from the queue: `billing.retry.0`, `billing.retry.1`, … and `billing.parked`.
 * Everything is published through the default exchange, so none of it needs a binding.
 */
suspend fun <T> Amqp.retryQueue(
    queue: String,
    codec: AmqpCodec<T>,
    policy: RetryPolicy = RetryPolicy.Default,
): RetryQueue<T> {
    val delayQueues = policy.delays.indices.map { "$queue.retry.$it" }
    val parked = "$queue.parked"

    val topology =
        declare {
            policy.delays.forEachIndexed { attempt, delay ->
                queue(delayQueues[attempt]) {
                    messageTtl = delay
                    /* Expired here means delivered there: the default exchange routes by queue
                       name, so the way back needs no exchange and no binding of its own. */
                    deadLetterTo("", routingKey = queue)
                }
            }
            queue(parked)
        }

    return RetryQueue(
        queue = queue,
        policy = policy,
        delayQueues = delayQueues,
        parked = parked,
        topology = topology,
        publisher = publisher("", codec, PublisherOptions(mandatory = true)),
    )
}

/** [retryQueue] for a body that is JSON, serialized through this connection's `Json`. */
suspend inline fun <reified T> Amqp.retryQueue(
    queue: String,
    policy: RetryPolicy = RetryPolicy.Default,
    json: Json = this.json,
): RetryQueue<T> = retryQueue(queue, jsonCodec<T>(json), policy)

/** Takes down the delay and parking queues — for a test, or a queue being retired. */
suspend fun Amqp.delete(retries: RetryQueue<*>) = delete(retries.topology)

/**
 * Hands every message to [handler], and sends the ones that fail round [retries] instead of
 * rejecting them.
 *
 * The original is acknowledged only once its copy has been published and confirmed, so a failure in
 * between leaves the message on the queue rather than nowhere. Acknowledging it is what stops the
 * broker from redelivering the very message that is now waiting in a delay queue.
 */
suspend fun <T> AmqpConsumer<T>.processWithRetry(
    retries: RetryQueue<T>,
    onFailure: (AmqpMessage<T>, Throwable) -> Unit = { _, _ -> },
    handler: suspend (AmqpMessage<T>) -> Unit,
) = coroutineScope {
    val inFlight = Semaphore(options.concurrency)

    suspend fun handle(message: AmqpMessage<T>) {
        try {
            handler(message)
            ack(message)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            onFailure(message, failure)
            try {
                retries.retry(message, failure)
                ack(message)
            } catch (republish: Throwable) {
                /* The copy did not make it. Back on the queue is the only place left that is not
                   "lost", even though it means an immediate redelivery. */
                if (republish is CancellationException) throw republish
                runCatching { nack(message, requeue = true) }
            }
        }
    }

    messages().collect { message ->
        if (options.concurrency == 1) handle(message) else launch { inFlight.withPermit { handle(message) } }
    }
}
