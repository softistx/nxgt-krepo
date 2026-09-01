package com.softistx.storage.bucket

import kotlin.time.Instant

/** What the store knows about an object without being asked for its bytes. */
data class ObjectInfo(
    val key: String,
    val size: Long,
    val lastModified: Instant?,
    val etag: String?,
    val contentType: String? = null,
)

/** What a write answers with. The etag is the store's word for "this is the version you just made". */
data class StoredObject(
    val key: String,
    val etag: String?,
    val versionId: String? = null,
)
