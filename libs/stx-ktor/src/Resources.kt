package com.strange.ktor

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.util.AttributeKey

/*
 * The four verbs below are **the contract between this module and every integration built on it**,
 * which is why they are public rather than internal.
 *
 * They were internal while every plugin lived in this module. They stopped being able to be the day
 * the first integration moved into its own module beside the library it integrates — see
 * `stx-workflow-ktor` — and that is the right way round: the resource-lifecycle idiom belongs to no
 * particular integration, so it stays here, and an integration is a caller of it like any other.
 *
 * A plugin that opens something uses `own`; one that is handed something uses `publish`; one that
 * accepts either uses `resource`; and anything reading what a plugin put on the application uses
 * `required`, so a missing `install` says so by name.
 */

/**
 * What every plugin built on this module does with the connection it opened.
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
fun <T : AutoCloseable> Application.own(
    key: AttributeKey<T>,
    resource: T,
): T {
    attributes.put(key, resource)
    monitor.subscribe(ApplicationStopped) { runCatching { resource.close() } }
    return resource
}

/**
 * Puts [resource] on the application without taking responsibility for closing it.
 *
 * The counterpart to [own], and the reason both exist: a resource someone else built is closed by
 * whoever built it. Ktor's own DI closes every `AutoCloseable` it created when the application
 * stops, and Koin closes through `onClose` — a plugin that adopts one of those and also closes it
 * closes it twice, which no `close()` in these libraries promises to survive.
 */
fun <T : Any> Application.publish(
    key: AttributeKey<T>,
    resource: T,
): T {
    attributes.put(key, resource)
    return resource
}

/**
 * The rule every plugin here follows, in one place: **whoever created it closes it.**
 *
 * [provided] is what the application handed in — from a DI container, or built by hand. When there
 * is one it is published and otherwise left alone; when there is not, [create] makes one and this
 * closes it on [ApplicationStopped].
 */
fun <T : AutoCloseable> Application.resource(
    key: AttributeKey<T>,
    provided: T?,
    create: () -> T,
): T = if (provided != null) publish(key, provided) else own(key, create())

/**
 * Reads what a plugin put on the application, or names the plugin that is missing.
 *
 * Throwing beats returning null. A route that reaches for a connection has already decided it needs
 * one, and a service whose first request fails loudly on a missing `install` is in better shape than
 * one that discovers it as a null three layers down.
 */
fun <T : Any> Application.required(
    key: AttributeKey<T>,
    plugin: String,
): T =
    attributes.getOrNull(key)
        ?: error("the $plugin plugin is not installed — call install($plugin) { … } first")
