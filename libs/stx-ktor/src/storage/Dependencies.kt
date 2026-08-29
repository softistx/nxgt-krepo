package com.strange.ktor.storage

import com.strange.storage.ObjectStorage
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Makes the object-storage client the plugin installed injectable, without opening a second one.
 *
 * ```kotlin
 * install(Storage) { config = StorageConfig(…) }
 * provideStorage()
 *
 * class Avatars(private val storage: ObjectStorage)   // built by the container, no ApplicationCall in sight
 * ```
 *
 * Or in one line, which is the same thing: `install(Storage) { config = StorageConfig(…); injectable = true }`.
 *
 * **The container closes it at application stop, and that is not a problem.** Ktor's DI closes every
 * `AutoCloseable` it hands out — one a provider merely passed through included, which a spec in
 * this module pins, and a per-key `cleanup` runs in addition to that rather than instead of it. So
 * this resource is closed by the container as well as by whoever created it, and both are safe
 * because these clients close idempotently: see `CloseGuard` in `stx-common`. What it does mean
 * is that a connection which has to outlive the application should not be registered here.
 */
fun Application.provideStorage() {
    val client = storage
    dependencies {
        provide<ObjectStorage> { client }
    }
}
