package com.strange.amqp

import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * The broker the integration specs run against, and the rule they all follow.
 *
 * `AMQP_TEST_URI` has **no default**, and the specs that need a broker skip when it is unset. The
 * URI carries the credentials, and a credential with a default is a credential in source control —
 * the same rule the storage specs follow for MinIO. For the broker this workspace runs:
 *
 * ```bash
 * set -a; . ~/workspace/docker/apps/rabbitmq/.env; set +a
 * AMQP_TEST_URI="amqp://$RABBITMQ_DEFAULT_USER:$RABBITMQ_DEFAULT_PASS@localhost:5672/%2F" ./kotlin test -m shared-amqp
 * ```
 *
 * The `%2F` is the default vhost and not decoration — a plain trailing `/` is the *empty* vhost,
 * and the broker refuses it with a message about permissions that says nothing about the cause.
 *
 * That broker is shared with everything else on this machine, so nothing here touches a queue or an
 * exchange it did not create: every name comes from [name], which is unique per run, and every spec
 * deletes what it declared.
 */
object AmqpTestBroker {
    val uri: String? = System.getenv("AMQP_TEST_URI")

    /** Whether a broker answered — checked once, so a machine without one skips instead of hanging. */
    val available: Boolean by lazy {
        val address = uri ?: return@lazy false
        runCatching {
            runBlocking { Amqp.connect(AmqpConfig(uri = address, connectionName = "shared-amqp probe")).use { it.isOpen } }
        }.getOrDefault(false)
    }

    suspend fun connect(name: String = "shared-amqp tests"): Amqp =
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
