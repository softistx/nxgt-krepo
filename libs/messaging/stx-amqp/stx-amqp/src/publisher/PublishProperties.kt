package com.softistx.amqp.publisher

import com.rabbitmq.client.AMQP
import com.softistx.amqp.codec.AmqpCodec
import com.softistx.amqp.message.MessageHeaders
import kotlin.time.Duration
import kotlin.time.Instant

private const val PERSISTENT = 2
private const val TRANSIENT = 1

/** The AMQP properties of one published message, from what the caller set and the publisher's options. */
internal fun publishProperties(
    codec: AmqpCodec<*>,
    options: PublisherOptions,
    headers: MessageHeaders,
    messageId: String?,
    correlationId: String?,
    replyTo: String?,
    expiration: Duration?,
    priority: Int?,
    timestamp: Instant?,
): AMQP.BasicProperties =
    AMQP.BasicProperties
        .Builder()
        .contentType(codec.contentType)
        .deliveryMode(if (options.persistent) PERSISTENT else TRANSIENT)
        .headers(headers.asPublished().ifEmpty { null })
        .messageId(messageId)
        .correlationId(correlationId)
        .replyTo(replyTo)
        .expiration(expiration?.inWholeMilliseconds?.toString())
        .priority(priority)
        .appId(options.appId)
        .timestamp(timestamp?.let { java.util.Date(it.toEpochMilliseconds()) })
        .build()
