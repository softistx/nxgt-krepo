package com.softistx.amqp.consumer

import com.softistx.amqp.message.AmqpMessage
import kotlinx.coroutines.CancellationException

/** Runs [handler] on one message, then acknowledges or rejects it on the terms [ConsumerOptions] set. */
internal suspend fun <T> AmqpConsumer<T>.handle(
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
    if (options.ack is AckStrategy.AfterHandler) ackAfterHandler(message)
}
