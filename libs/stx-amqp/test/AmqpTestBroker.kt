package com.strange.amqp

import com.strange.testing.containers.rabbitContainer
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * The broker the integration specs run against: one started for this run, unless `AMQP_TEST_URI`
 * names one that is already up.
 *
 * `AMQP_TEST_URI` still has **no default** and never will — the URI carries its credentials, and a
 * credential with a default is a credential in source control. What changed is that its absence is
 * no longer a reason to skip: a container hands out credentials of its own, so these specs now run
 * on a machine where nobody exported anything. Before, they quietly proved nothing there.
 *
 * To point them at the broker this workspace runs instead:
 *
 * ```bash
 * set -a; . ~/workspace/docker/apps/rabbitmq/.env; set +a
 * AMQP_TEST_URI="amqp://$RABBITMQ_DEFAULT_USER:$RABBITMQ_DEFAULT_PASS@localhost:5672/%2F" ./kotlin test -m stx-amqp
 * ```
 *
 * The `%2F` is the default vhost and not decoration — a plain trailing `/` is the *empty* vhost,
 * and the broker refuses it with a message about permissions that says nothing about the cause. The
 * container's URI sidesteps it by carrying no vhost path at all.
 *
 * A reused broker is shared with everything else on that machine, so nothing here touches a queue
 * or an exchange it did not create: every name comes from [name], which is unique per run, and
 * every spec deletes what it declared.
 */
object AmqpTestBroker {
    private val broker = rabbitContainer()

    val uri: String? get() = broker.endpoint

    /** Whether a broker answered — checked once, so a machine without one skips instead of hanging. */
    val available: Boolean by lazy {
        val address = uri ?: return@lazy false
        runCatching {
            runBlocking { Amqp.connect(AmqpConfig(uri = address, connectionName = "stx-amqp probe")).use { it.isOpen } }
        }.getOrDefault(false)
    }

    suspend fun connect(name: String = "stx-amqp tests"): Amqp =
        Amqp.connect(
            AmqpConfig(
                uri = requireNotNull(uri) { "AMQP_TEST_URI is unset — this spec should have been skipped" },
                connectionName = name,
            ),
        )

    /** A connection for one spec, closed afterwards however the spec ends. */
    suspend fun <T> amqp(block: suspend (Amqp) -> T): T = connect().use { block(it) }

    /** A name nothing else on this broker will have. */
    fun name(prefix: String): String = "test.$prefix.${UUID.randomUUID().toString().take(8)}"
}
