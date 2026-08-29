package com.strange.ktor.storage

import com.strange.ktor.required
import com.strange.storage.ObjectStorage
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The application's object storage, as [Storage] opened it. */
val Application.storage: ObjectStorage get() = required(StorageKey, "Storage")

/** The same client, from a route. */
val ApplicationCall.storage: ObjectStorage get() = application.storage
