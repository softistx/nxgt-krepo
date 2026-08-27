package com.strange.storage

import com.strange.common.lifecycle.CloseGuard
import com.strange.storage.bucket.StorageBucket
import io.minio.BucketExistsArgs
import io.minio.CreateBucketArgs
import io.minio.MinioAsyncClient
import io.minio.RemoveBucketArgs
import kotlinx.coroutines.future.await

/**
 * A connection to an S3-compatible store, and the buckets on it.
 *
 * ```kotlin
 * val storage = ObjectStorage.connect(StorageConfig(endpoint, accessKey, secretKey))
 * val uploads = storage.ensureBucket("uploads")
 * ```
 *
 * The client underneath is `MinioAsyncClient`, whose futures this awaits. What it is *not* is the
 * synchronous `MinioClient`: that one blocks the calling thread on every call, and a suspending
 * wrapper around it would only be moving the block somewhere the caller cannot see.
 *
 * One client is the right number — it owns an OkHttp connection pool and is thread-safe. [close]
 * shuts that pool down, so a client that is closed while a download is still being read takes the
 * download with it.
 */
class ObjectStorage internal constructor(
    internal val client: MinioAsyncClient,
    internal val endpoint: String,
) : AutoCloseable {
    private val guard = CloseGuard()

    /** Every bucket this credential can see. */
    suspend fun buckets(): List<String> = client.listBuckets().await().map { it.name() }

    suspend fun bucketExists(name: String): Boolean = client.bucketExists(BucketExistsArgs.builder().bucket(name).build()).await()

    /**
     * Creates [name] if it is not already there, and hands back a handle either way.
     *
     * Idempotent by catching `BucketAlreadyOwnedByYou` rather than by asking first: two instances
     * starting together would both be told it does not exist and both try to create it, and the
     * loser needs this anyway.
     */
    suspend fun ensureBucket(name: String): StorageBucket {
        absentAsNull(BUCKET_ALREADY_OWNED) {
            client.createBucket(CreateBucketArgs.builder().bucket(name).build()).await()
        }
        return bucket(name)
    }

    /** Deletes an *empty* bucket — S3 refuses to delete one that still has objects in it. */
    suspend fun deleteBucket(name: String) {
        client.removeBucket(RemoveBucketArgs.builder().bucket(name).build()).await()
    }

    /**
     * A handle on [name], without asking whether it exists — the check is a round trip, and every
     * operation on the handle will tell the caller soon enough.
     */
    fun bucket(name: String): StorageBucket = StorageBucket(client, name, endpoint)

    /** Closes the client. Calling it again does nothing. */
    override fun close() = guard.once { client.close() }

    companion object {
        fun connect(config: StorageConfig): ObjectStorage {
            val client =
                MinioAsyncClient
                    .builder()
                    .endpoint(config.endpoint)
                    .credentials(config.accessKey, config.secretKey)
                    .apply { config.region?.let { region(it) } }
                    .build()
            return ObjectStorage(client, config.endpoint.trimEnd('/'))
        }
    }
}
