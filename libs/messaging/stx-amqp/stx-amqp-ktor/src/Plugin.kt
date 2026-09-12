package com.softistx.amqp.ktor

import com.softistx.amqp.Amqp
import com.softistx.amqp.AmqpConfig
import com.softistx.ktor.resource
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking

/**
 * One AMQP connection for the application, closed when it stops.
 *
 * ```kotlin
 * install(AmqpConnection) { config = AmqpConfig(uri = System.getenv("AMQP_URI"), connectionName = "orders-api") }
 *
 * post("/orders") { call.amqp.publisher<OrderPlaced>("orders").use { it.publish(order) } }
 * ```
 *
 * A connection multiplexes and a channel does not, which is why this owns the first and nothing
 * else: channels, publishers and consumers are opened by whoever needs one and closed by them.
 * `Amqp.openChannel` and `withChannel` are how a route gets one.
 *
 * **Installing it registers the connection with Ktor's DI**, so a class the container builds takes an
 * [Amqp] in its constructor rather than reaching through a call. See [provideAmqp] for what the
 * container's second claim on closing it means.
 *
 * **The connect is blocking here, and on purpose.** `Amqp.connect` suspends and plugin installation
 * does not, so this is the one place in the module that calls `runBlocking` — at startup, on the
 * thread that is starting the application, before anything is serving. The alternative is a server
 * that accepts requests while its broker connection is still being made, and answers the first of
 * them with a failure that looks like the broker's fault.
 */
val AmqpConnection =
    createApplicationPlugin(name = "Amqp", createConfiguration = ::AmqpConnectionConfiguration) {
        application.resource(AmqpKey, pluginConfig.instance) { runBlocking { Amqp.connect(pluginConfig.config) } }
        application.provideAmqp()
    }

/** What [AmqpConnection] connects with. */
class AmqpConnectionConfiguration {
    /**
     * The URI, the connection name, heartbeats and recovery.
     *
     * Worth setting `connectionName`: it is what the broker's management UI shows, and "orders-api"
     * beats an anonymous connection when something has to be traced back to a service.
     */
    var config: AmqpConfig = AmqpConfig()

    /**
     * A connection built elsewhere — by a DI container, or by hand.
     *
     * When set, [config] is ignored and this is **not** closed when the application stops: whoever created
     * it closes it. That is what lets a container own the connection while routes still reach it
     * through `call.amqp`.
     */
    var instance: Amqp? = null
}

internal val AmqpKey = AttributeKey<Amqp>("com.softistx.amqp.Amqp")
