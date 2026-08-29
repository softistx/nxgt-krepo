package com.strange.ktor.amqp

import com.strange.amqp.Amqp
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Makes the AMQP connection the plugin installed injectable, without opening a second one.
 *
 * ```kotlin
 * install(AmqpConnection) { config = AmqpConfig(uri = …) }
 * provideAmqp()
 *
 * class OrderEvents(private val amqp: Amqp)   // built by the container, no ApplicationCall in sight
 * ```
 *
 * Or in one line, which is the same thing: `install(AmqpConnection) { config = AmqpConfig(uri = …); injectable = true }`.
 *
 * **The container closes it at application stop, and that is not a problem.** Ktor's DI closes every
 * `AutoCloseable` it hands out — one a provider merely passed through included, which a spec in
 * this module pins, and a per-key `cleanup` runs in addition to that rather than instead of it. So
 * this resource is closed by the container as well as by whoever created it, and both are safe
 * because these clients close idempotently: see `CloseGuard` in `stx-common`. What it does mean
 * is that a connection which has to outlive the application should not be registered here.
 */
fun Application.provideAmqp() {
    val connection = amqp
    dependencies {
        provide<Amqp> { connection }
    }
}
