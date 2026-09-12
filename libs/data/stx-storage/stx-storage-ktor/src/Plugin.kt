package com.softistx.storage.ktor

import com.softistx.ktor.resource
import com.softistx.storage.ObjectStorage
import com.softistx.storage.StorageConfig
import io.ktor.server.application.*
import io.ktor.util.*

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
 *
 * **Installing it registers the client with Ktor's DI**, so a class the container builds takes an
 * [ObjectStorage] in its constructor rather than reaching through a call. See [provideStorage] for
 * what the container's second claim on closing it means.
 */
val Storage =
    createApplicationPlugin(name = "Storage", createConfiguration = ::StorageConfiguration) {
        application.resource(StorageKey, pluginConfig.instance) {
            val config = requireNotNull(pluginConfig.config) { "install(Storage) needs `config` or `instance`" }
            ObjectStorage.connect(config)
        }
        application.provideStorage()
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
}

internal val StorageKey = AttributeKey<ObjectStorage>("com.softistx.storage.ObjectStorage")
