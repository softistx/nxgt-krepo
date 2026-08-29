package com.strange.koin.amqp

import com.strange.amqp.Amqp
import com.strange.amqp.AmqpConfig
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * One AMQP connection for the container to hand out, closed when the container stops.
 *
 * ```kotlin
 * startKoin { modules(amqpModule(AmqpConfig(uri = System.getenv("AMQP_URI"))), appModule) }
 * ```
 *
 * **`runBlocking`, and it is the one place here that needs it.** `Amqp.connect` suspends because it
 * is a socket, a handshake and an authentication round trip; Koin's `single { }` does not suspend
 * and has no suspending form. The same trade `AmqpConnection` makes at plugin install, on the
 * thread that is starting the application either way — and the reason to prefer resolving this
 * eagerly at startup rather than the first time a class asks for it.
 *
 * In an application that also serves HTTP, install the plugin over this connection rather than
 * opening a second: `install(AmqpConnection) { instance = get() }`.
 */
fun amqpModule(config: AmqpConfig = AmqpConfig()): Module =
    module {
        single { runBlocking { Amqp.connect(config) } } onClose { it?.close() }
    }
