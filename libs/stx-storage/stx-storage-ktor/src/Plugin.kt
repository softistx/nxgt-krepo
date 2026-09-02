package com.softistx.storage.ktor

import com.softistx.ktor.resource
import com.softistx.storage.ObjectStorage
import com.softistx.storage.StorageConfig
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey

/**
 * One object-storage client for the application, closed when it stops.
 *
 * ```kotlin
 * install(Storage) { config = StorageConfig(endpoint, accessKey, secretKey) }
 *
 * get("/avatar/{key}") { call.respondText(call.storage.bucket("avatars").presignedGet(key)) }
 * ```
 *
 * [StorageConfig] is required and has no default, unlike the other plugins here, because two of its
 * three fields are credentials — and a credential with a default is a credential in source control.
 */
val Storage =
    createApplicationPlugin(name = "Storage", createConfiguration = ::StorageConfiguration) {
        application.resource(StorageKey, pluginConfig.instance) {
            val config = requireNotNull(pluginConfig.config) { "install(Storage) needs `config` or `instance`" }
            ObjectStorage.connect(config)
        }
        if (pluginConfig.injectable) application.provideStorage()
    }

/** What [Storage] connects with. */
class StorageConfiguration {
    /** Endpoint and credentials. Required: there is no safe default for somebody else's keys. */
    var config: StorageConfig? = null

    /**
     * A client built elsewhere — by a DI container, or by hand.
     *
     * When set, [config] is not needed and this is **not** closed when the application stops: whoever created
     * it closes it. That is what lets a container own the client while routes still reach it
     * through `call.storage`.
     */
    var instance: ObjectStorage? = null

    /**
     * Registers the client with Ktor's DI as well, so a class the container builds can take a
     * [ObjectStorage] in its constructor — the same one `call.storage` hands a route.
     *
     * Off by default, and it has to be: `ktor-server-di` is compile-only in this module, so an
     * application that never asks for this must not be made to carry it at runtime. Setting it
     * calls [provideStorage], which lives in its own file for that reason — nothing loads a class
     * from Ktor's DI until the flag is true.
     *
     * The container closes what it hands out when the application stops, so this hands it a second
     * claim on closing the client. That is safe — these clients close idempotently — but a
     * client that has to outlive the application does not belong in it.
     */
    var injectable: Boolean = false
}

internal val StorageKey = AttributeKey<ObjectStorage>("com.softistx.storage.ObjectStorage")
