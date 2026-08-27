package com.strange.ktor

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.util.AttributeKey

/**
 * What every plugin in this module does with the connection it opened.
 *
 * Puts [resource] on the application and closes it when the application stops. Written once because
 * the alternative is five plugins each subscribing to the same event, and the one that forgets is
 * the one that leaks a connection pool per redeploy — a mistake nothing fails on, until a broker
 * runs out of file handles.
 *
 * [ApplicationStopped] rather than `ApplicationStopping`: stopping fires while requests may still be
 * in flight, and a request that finds its connection already closed is a 500 caused by the shutdown
 * rather than by anything the caller did.
 */
internal fun <T : AutoCloseable> Application.own(
    key: AttributeKey<T>,
    resource: T,
): T {
    attributes.put(key, resource)
    monitor.subscribe(ApplicationStopped) { runCatching { resource.close() } }
    return resource
}

/**
 * Reads what a plugin put on the application, or names the plugin that is missing.
 *
 * Throwing beats returning null. A route that reaches for a connection has already decided it needs
 * one, and a service whose first request fails loudly on a missing `install` is in better shape than
 * one that discovers it as a null three layers down.
 */
internal fun <T : Any> Application.required(
    key: AttributeKey<T>,
    plugin: String,
): T =
    attributes.getOrNull(key)
        ?: error("the $plugin plugin is not installed — call install($plugin) { … } first")
