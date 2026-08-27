package com.strange.storage

import com.strange.storage.bucket.StorageBucket
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

/**
 * The MinIO this workspace already runs — `~/workspace/docker/apps/minio` publishes it on port 9000.
 *
 * The credentials come from the environment and have no default, which is the same rule
 * [StorageConfig] follows and for the same reason. Without them these specs report as skipped:
 *
 * ```bash
 * export MINIO_TEST_ACCESS_KEY=... MINIO_TEST_SECRET_KEY=...
 * ./kotlin test -m shared-storage
 * ```
 *
 * Every spec works in a bucket of its own, named after the run, and removes it afterwards — the
 * server holds other applications' buckets and none of this may touch them.
 */
internal object MinioTestServer {
    private val endpoint = System.getenv("MINIO_TEST_ENDPOINT") ?: "http://localhost:9000"
    private val accessKey: String? = System.getenv("MINIO_TEST_ACCESS_KEY")
    private val secretKey: String? = System.getenv("MINIO_TEST_SECRET_KEY")

    private val buckets = AtomicInteger()

    private fun config(): StorageConfig? =
        if (accessKey.isNullOrEmpty() || secretKey.isNullOrEmpty()) {
            null
        } else {
            StorageConfig(endpoint, accessKey, secretKey)
        }

    val available: Boolean by lazy {
        val config = config() ?: return@lazy false
        runCatching { ObjectStorage.connect(config).use { runBlocking { it.buckets() } } }.isSuccess
    }

    fun connect(): ObjectStorage = ObjectStorage.connect(config() ?: error("MINIO_TEST_ACCESS_KEY is not set"))

    /** A bucket name no other spec is using, and legal: lowercase, hyphens, under 63 characters. */
    fun bucketName(): String = "shared-storage-test-${buckets.incrementAndGet()}-${System.nanoTime()}"

    suspend fun withStorage(block: suspend (ObjectStorage) -> Unit) = connect().use { block(it) }

    /** A bucket of its own, emptied and removed when [block] returns — S3 will not delete a full one. */
    suspend fun withBucket(block: suspend (StorageBucket) -> Unit) =
        withStorage { storage ->
            val name = bucketName()
            val bucket = storage.ensureBucket(name)
            try {
                block(bucket)
            } finally {
                bucket.deleteAll(bucket.list().map { it.key }.toList())
                storage.deleteBucket(name)
            }
        }
}
