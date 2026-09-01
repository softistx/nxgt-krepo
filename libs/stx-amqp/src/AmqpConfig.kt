package com.softistx.amqp

import com.rabbitmq.client.ConnectionFactory
import com.softistx.amqp.codec.amqpJson
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Where the broker is, and how this application talks to it.
 *
 * [uri] carries the whole address — host, port, virtual host, and the credentials if there are any:
 * `amqp://user:secret@rabbit:5672/billing`. The default names no credentials on purpose, so the
 * client falls back to the broker's own guest account for a local broker and a deployment has to
 * supply them from its environment rather than inherit them from a source file.
 *
 * **The virtual host is the isolation boundary**, and it is the part of the URI most often got
 * wrong. Two applications on one broker with the same vhost share one flat namespace of exchanges
 * and queues; with different vhosts they cannot see each other's at all.
 *
 * It is also where AMQP hides its sharpest edge: the default vhost is *named* `/`, and a URI path
 * of `/` means the **empty** vhost rather than that one. `amqp://localhost:5672/` therefore
 * authenticates against a vhost most brokers do not have, and fails with an error about
 * permissions rather than about spelling. The default vhost has to be escaped:
 *
 * ```
 * amqp://localhost:5672          the default vhost — no path at all
 * amqp://localhost:5672/%2F      the same thing, spelled out
 * amqp://localhost:5672/billing  a vhost named billing
 * amqp://localhost:5672/         the empty vhost, which is probably not what was meant
 * ```
 *
 * [heartbeat] is what makes a dead connection *look* dead. Without it a broker restart or a dropped
 * NAT mapping leaves a socket that reads as open and delivers nothing, and the client sits there.
 *
 * [json] is what every typed publisher and consumer serializes through, so a service configures its
 * message bodies once rather than once per queue. See [amqpJson] for what the default is lenient
 * about and why.
 *
 * [configure] is the escape hatch: the client is configured through a `ConnectionFactory` rather
 * than a property map, so this hands over the real thing after everything above has been applied,
 * for the TLS, SASL and recovery knobs this module has no opinion about.
 */
data class AmqpConfig(
    val uri: String = "amqp://localhost:5672",
    val connectionName: String? = null,
    val heartbeat: Duration = 60.seconds,
    val connectionTimeout: Duration = 10.seconds,
    val recovery: Boolean = true,
    val json: Json = amqpJson,
    val configure: (ConnectionFactory) -> Unit = {},
) {
    init {
        require(heartbeat >= Duration.ZERO) { "a heartbeat is not negative: $heartbeat" }
        require(connectionTimeout > Duration.ZERO) { "a connection timeout is positive: $connectionTimeout" }
    }
}
