package com.softistx.jpa.ktor

import com.softistx.jpa.Jpa
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Registers the session factory the plugin built with Ktor's DI, without building a second one.
 *
 * ```kotlin
 * install(JpaConnection) { config = JpaConfig(uri = …); entities(Order::class) }
 *
 * class Orders(private val jpa: Jpa)   // built by the container, no ApplicationCall in sight
 * ```
 *
 * Called by [JpaConnection] at install — there is nothing to switch on.
 *
 * **The container closes it at application stop, and that is not a problem.** Ktor's DI closes every
 * `AutoCloseable` it hands out — one a provider merely passed through included, which a spec in this
 * module pins, and a per-key `cleanup` runs in addition to that rather than instead of it. So the
 * factory is closed by the container as well as by whoever created it, and both are safe because
 * `Jpa.close` goes through `CloseGuard`.
 */
internal fun Application.provideJpa() {
    val factory = jpa
    dependencies {
        provide<Jpa> { factory }
    }
}
