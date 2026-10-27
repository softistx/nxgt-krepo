package com.softistx.amqp.publisher

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
