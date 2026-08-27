package com.strange.storage.bucket

import io.minio.MinioAsyncClient

/** One bucket's objects. Filled in by the next slice. */
class StorageBucket internal constructor(
    internal val client: MinioAsyncClient,
    val name: String,
)
