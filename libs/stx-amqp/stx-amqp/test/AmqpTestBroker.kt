package com.softistx.amqp

import com.softistx.testing.containers.TestNames
import com.softistx.testing.containers.rabbitContainer
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

/**
 * The broker the integration specs run against: one started for this run, unless `AMQP_TEST_URI`
 * names one that is already up.
 *
 * `AMQP_TEST_URI` still has **no default** and never will — the URI carries its credentials, and a
 * credential with a default is a credential in source control. What changed is that its absence is
 * no longer a reason to skip: a container hands out credentials of its own, so these specs now run
 * on a machine where nobody exported anything. Before, they quietly proved nothing there.
 *
 * To point them at a broker that is already running instead:
 *
 * ```bash
 * AMQP_TEST_URI="amqp://user:password@localhost:5672/%2F" ./kotlin test -m stx-amqp
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
internal object AmqpTestBroker {
    private val broker = rabbitContainer()

    val uri: String get() = broker.requireEndpoint()

    /** Whether a broker answered — checked once, so a machine without one skips instead of hanging. */
    val available: Boolean by lazy {
        broker.available &&
            runCatching {
                runBlocking { Amqp.connect(AmqpConfig(uri = uri, connectionName = "stx-amqp probe")).use { it.isOpen } }
            }.getOrDefault(false)
    }

    suspend fun connect(name: String = "stx-amqp tests"): Amqp =
        Amqp.connect(
            AmqpConfig(
                uri = uri,
                connectionName = name,
            ),
        )

    /** A connection for one spec, closed afterwards however the spec ends. */
    suspend fun <T> amqp(block: suspend (Amqp) -> T): T = connect().use { block(it) }

    /**
     * A name nothing else on this broker will have: `test.<prefix>.<n>.<run>`.
     *
     * Built here rather than by [TestNames.next] because an AMQP name is dotted and wants the
     * caller's [prefix] in the middle, not at the front — but [TestNames.RUN] is the same run
     * identifier every other harness suffixes with, and it is there for the same reason: a bare
     * counter restarts at 1 in the next JVM, and a reused broker still holds what a crashed run
     * declared.
     */
    fun name(prefix: String): String = "test.$prefix.${counter.incrementAndGet()}.${TestNames.RUN}"

    private val counter = AtomicInteger()
}
