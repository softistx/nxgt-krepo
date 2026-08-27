package com.strange.koin.storage

import com.strange.storage.ObjectStorage
import com.strange.storage.StorageConfig
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * One object-storage client for the container to hand out, closed when the container stops.
 *
 * ```kotlin
 * startKoin { modules(storageModule(StorageConfig(endpoint = …, accessKey = …, secretKey = …))) }
 * ```
 *
 * In an application that also serves HTTP: `install(Storage) { instance = get() }`.
 */
fun storageModule(config: StorageConfig): Module =
    module {
        single { ObjectStorage.connect(config) } onClose { it?.close() }
    }
