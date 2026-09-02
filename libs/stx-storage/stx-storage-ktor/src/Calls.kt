package com.softistx.storage.ktor

import com.softistx.ktor.required
import com.softistx.storage.ObjectStorage
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The application's object storage, as [Storage] opened it. */
val Application.storage: ObjectStorage get() = required(StorageKey, "Storage")

/** The same client, from a route. */
val ApplicationCall.storage: ObjectStorage get() = application.storage
