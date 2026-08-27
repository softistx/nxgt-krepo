package com.strange.storage

import com.strange.storage.bucket.StorageBucket
import com.strange.testing.containers.minioContainer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

/**
 * The MinIO the integration specs talk to: one started for this run, unless
 * `MINIO_TEST_ACCESS_KEY` and `MINIO_TEST_SECRET_KEY` name a store that is already up — the
 * workspace's own on port 9000, or one CI provisioned.
 *
 * Those two still have **no defaults**, which is the rule [StorageConfig] follows and for the same
 * reason: a credential with a default is a credential in source control. They live in
 * `~/workspace/docker/apps/minio/.env` and are exported for a run, never committed:
 *
 * ```bash
 * set -a; . ~/workspace/docker/apps/minio/.env; set +a
 * MINIO_TEST_ACCESS_KEY=$MINIO_ROOT_USER MINIO_TEST_SECRET_KEY=$MINIO_ROOT_PASSWORD ./kotlin test -m shared-storage
 * ```
 *
 * What changed is that their absence no longer means skipping: a container comes with its own key
 * pair. `MINIO_TEST_ENDPOINT` alone is not enough to take the override — an endpoint without a way
 * in fails later and less clearly than starting a container would.
 *
 * Every spec works in a bucket of its own, named after the run, and removes it afterwards — a
 * reused server holds other applications' buckets and none of this may touch them.
 */
internal object MinioTestServer {
    private val minio = minioContainer()

    val endpoint: String get() = requireNotNull(minio.endpoint) { minio.describe() }.url

    private val buckets = AtomicInteger()

    private fun config(): StorageConfig? = minio.endpoint?.let { StorageConfig(it.url, it.accessKey, it.secretKey) }

    val available: Boolean by lazy {
        val config = config() ?: return@lazy false
        runCatching { ObjectStorage.connect(config).use { runBlocking { it.buckets() } } }.isSuccess
    }

    fun connect(): ObjectStorage = ObjectStorage.connect(config() ?: error(minio.describe()))

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
