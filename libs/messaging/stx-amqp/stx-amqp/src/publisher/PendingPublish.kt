package com.softistx.amqp.publisher

import com.softistx.amqp.AmqpClosedException
import com.softistx.amqp.AmqpNackException
import com.softistx.amqp.AmqpUnroutableException
import kotlinx.coroutines.CompletableDeferred

/** A publish the broker has not spoken about yet. Touched only by the settler once registered. */
internal class Pending(
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
internal sealed interface Confirm {
    data class Registered(
        val entry: Pending,
        val epoch: Long,
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

    /** The channel shut down: nothing registered before this will ever be confirmed. */
    data class Lost(
        val epoch: Long,
    ) : Confirm
}
