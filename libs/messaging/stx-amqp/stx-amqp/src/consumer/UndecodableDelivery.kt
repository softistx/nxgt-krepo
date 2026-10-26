package com.softistx.amqp.consumer

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Delivery
import com.softistx.amqp.AmqpValueException

/** What a consumer does with a delivery its codec could not read, per [ConsumerOptions.undecodable]. */
internal fun rejectUndecodable(
    channel: Channel,
    options: ConsumerOptions,
    delivery: Delivery,
    failure: AmqpValueException,
    stop: (Throwable) -> Unit,
) {
    val autoAck = options.ack is AckStrategy.OnDelivery
    when (options.undecodable) {
        Undecodable.DeadLetter -> {
            if (!autoAck) runCatching { channel.basicReject(delivery.envelope.deliveryTag, false) }
        }

        Undecodable.Fail -> {
            if (!autoAck) runCatching { channel.basicNack(delivery.envelope.deliveryTag, false, true) }
            // Thrown from here it would close the channel and leave the collector waiting.
            stop(failure)
        }
    }
}
