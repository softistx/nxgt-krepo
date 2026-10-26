package com.softistx.amqp.consumer

import com.rabbitmq.client.ShutdownSignalException
import com.softistx.amqp.message.AmqpMessage
import kotlinx.coroutines.CancellationException

private val log: System.Logger = System.getLogger("com.softistx.amqp.consumer.AmqpConsumer")

/**
 * Whether [failure] says the channel or connection is gone: a shutdown signal, `AlreadyClosedException`
 * being one, or an `IOException` that carries one.
 */
internal fun isShutdown(failure: Throwable): Boolean =
    generateSequence(failure) { it.cause }.take(CAUSE_DEPTH).any { it is ShutdownSignalException }

/**
 * Acknowledges [message] after its handler returned, where a shutdown is not a failure.
 *
 * A handler that finished while the connection was down is not a failed handler, and the broker has
 * not lost anything: a message that was never acknowledged is redelivered once the channel is
 * back. So the failed acknowledgement is logged and the flow carries on, instead of ending with an
 * exception that would also have sent the message round the retries. Any other failure still
 * propagates.
 */
internal suspend fun <T> AmqpConsumer<T>.ackAfterHandler(message: AmqpMessage<T>) {
    try {
        ack(message)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        if (!isShutdown(failure)) throw failure
        log.log(
            System.Logger.Level.WARNING,
            "acknowledgement of delivery ${message.deliveryTag} on '$queue' lost to a shutdown; the broker redelivers it",
            failure,
        )
    }
}

private const val CAUSE_DEPTH = 8
