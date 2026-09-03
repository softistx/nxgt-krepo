package com.softistx.storage.ktor

import com.softistx.storage.ObjectStorage
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Registers the object-storage client the plugin opened with Ktor's DI, without opening a second one.
 *
 * ```kotlin
 * install(Storage) { config = StorageConfig(…) }
 *
 * class Avatars(private val storage: ObjectStorage)   // built by the container, no ApplicationCall in sight
 * ```
 *
 * Called by [Storage] at install — there is nothing to switch on.
 *
 * **The container closes it at application stop, and that is not a problem.** Ktor's DI closes every
 * `AutoCloseable` it hands out — one a provider merely passed through included, which a spec in
 * this module pins, and a per-key `cleanup` runs in addition to that rather than instead of it. So
 * this resource is closed by the container as well as by whoever created it, and both are safe
 * because these clients close idempotently: see `CloseGuard` in `stx-common`. What it does mean
 * is that a connection which has to outlive the application should not be registered here.
 */
internal fun Application.provideStorage() {
    val client = storage
    dependencies {
        provide<ObjectStorage> { client }
    }
}
