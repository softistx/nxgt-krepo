package com.softistx.amqp.publisher

/**
 * What a publish promises.
 *
 * [confirms] is the difference between "the socket accepted it" and "the broker has it". Without
 * publisher confirms a publish is fire-and-forget — it returns before the broker has looked at the
 * message, and a broker that dies a moment later takes it with it silently. On costs one round trip
 * and is the reason to use this module rather than the client directly.
 *
 * [mandatory] is the other half of the same question. A message published to an exchange with no
 * matching binding is *discarded and confirmed*: routed nowhere, reported as success. That is the
 * single most common way a working publisher delivers nothing, and turning this on turns it into an
 * exception naming the exchange and the routing key.
 *
 * [persistent] asks the broker to write the message to disk. It is half of durability and the half
 * people remember: a persistent message in a queue that was declared non-durable still dies with
 * the broker, because the queue itself does not come back.
 */
data class PublisherOptions(
    val confirms: Boolean = true,
    val mandatory: Boolean = false,
    val persistent: Boolean = true,
    val appId: String? = null,
)
