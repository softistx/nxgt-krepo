package com.softistx.amqp.publisher

/**
 * Where a message went, once the broker said it had it.
 *
 * [sequence] is the publish sequence number the confirm came back on — the broker's own handle for
 * this message on this channel, and what a log line needs to be matched against a confirm.
 */
data class Published(
    val exchange: String,
    val routingKey: String,
    val messageId: String?,
    val sequence: Long,
)
