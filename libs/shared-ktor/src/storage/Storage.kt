package com.strange.ktor.storage

import com.strange.ktor.own
import com.strange.storage.ObjectStorage
import com.strange.storage.StorageConfig
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
        val config = requireNotNull(pluginConfig.config) { "install(Storage) needs `config`" }
        application.own(StorageKey, ObjectStorage.connect(config))
    }

/** What [Storage] connects with. */
class StorageConfiguration {
    /** Endpoint and credentials. Required: there is no safe default for somebody else's keys. */
    var config: StorageConfig? = null
}

internal val StorageKey = AttributeKey<ObjectStorage>("com.strange.storage.ObjectStorage")
