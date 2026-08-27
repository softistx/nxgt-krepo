package com.strange.storage.bucket

import com.strange.storage.NO_SUCH_KEY
import com.strange.storage.ObjectNotFoundException
import com.strange.storage.absentAsNull
import io.minio.CopyObjectArgs
import io.minio.GetObjectArgs
import io.minio.ListObjectsArgs
import io.minio.MinioAsyncClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.RemoveObjectsArgs
import io.minio.SourceObject
import io.minio.StatObjectArgs
import io.minio.messages.DeleteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.time.toKotlinInstant

/**
 * One bucket's objects.
 *
 * Every method here is suspending or a `Flow`, and half of them are wrapped in [Dispatchers.IO]
 * rather than simply awaiting a future — because half of the MinIO SDK is not actually asynchronous:
 *
 * - `putObject` reads the stream it is given *before* it returns a future, on the calling thread.
 * - `getObject` answers with a future, and the `InputStream` inside it blocks on every read.
 * - `listObjects` and `removeObjects` return an `Iterable` that does network I/O as it is walked.
 *
 * Called from a coroutine without care, each of those pins whichever thread it lands on; on
 * `Dispatchers.Default` that is one of a handful shared by the whole application. Putting them on
 * the IO dispatcher is most of what this class is for.
 */
class StorageBucket internal constructor(
    internal val client: MinioAsyncClient,
    val name: String,
) {
    // ─── Writes ───────────────────────────────────────────────────────────────

    suspend fun put(
        key: String,
        bytes: ByteArray,
        contentType: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ): StoredObject = put(key, ByteArrayInputStream(bytes), bytes.size.toLong(), contentType, metadata)

    /**
     * Uploads [stream] under [key].
     *
     * [size] may be -1 for a stream of unknown length, which is what makes this usable for a
     * request body being relayed straight through; the SDK then buffers in 5 MiB parts to find out.
     * Passing the real size where it is known avoids that buffering entirely.
     *
     * The caller keeps ownership of [stream] and closes it.
     */
    suspend fun put(
        key: String,
        stream: InputStream,
        size: Long = -1,
        contentType: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ): StoredObject =
        withContext(Dispatchers.IO) {
            val response =
                client
                    .putObject(
                        PutObjectArgs
                            .builder()
                            .bucket(name)
                            .`object`(key)
                            .stream(stream, size, if (size < 0) PART_SIZE else -1)
                            .apply {
                                contentType?.let { contentType(it) }
                                if (metadata.isNotEmpty()) userMetadata(metadata)
                            }.build(),
                    ).await()
            StoredObject(key, response.etag(), response.versionId())
        }

    /** Copies within this bucket, or into [targetBucket]. Server-side: the bytes never come here. */
    suspend fun copy(
        sourceKey: String,
        targetKey: String,
        targetBucket: String = name,
    ): StoredObject =
        withContext(Dispatchers.IO) {
            val response =
                client
                    .copyObject(
                        CopyObjectArgs
                            .builder()
                            .bucket(targetBucket)
                            .`object`(targetKey)
                            .source(
                                SourceObject
                                    .builder()
                                    .bucket(name)
                                    .`object`(sourceKey)
                                    .build(),
                            ).build(),
                    ).await()
            StoredObject(targetKey, response.etag(), response.versionId())
        }

    // ─── Reads ────────────────────────────────────────────────────────────────

    /** The whole object, or null if there is none. Everything in memory — see [read] for the rest. */
    suspend fun get(key: String): ByteArray? = read(key) { it.readBytes() }

    /**
     * Hands [block] the object's stream, closing it afterwards, and answers null if there is no such
     * object.
     *
     * [offset] and [length] are a ranged read — the store sends only that part, which is what makes
     * serving a seek in a large file cheap.
     *
     * Only `NoSuchKey` becomes null. An expired credential and an object that was never uploaded
     * look identical to `catch { null }`, and only one of them is worth waking somebody for.
     */
    suspend fun <T> read(
        key: String,
        offset: Long? = null,
        length: Long? = null,
        block: suspend (InputStream) -> T,
    ): T? =
        withContext(Dispatchers.IO) {
            absentAsNull(NO_SUCH_KEY) {
                client
                    .getObject(
                        GetObjectArgs
                            .builder()
                            .bucket(name)
                            .`object`(key)
                            .apply {
                                offset?.let { offset(it) }
                                length?.let { length(it) }
                            }.build(),
                    ).await()
            }?.use { block(it) }
        }

    /** [get], for a caller that has nothing to say about an object that is not there. */
    suspend fun require(key: String): ByteArray = get(key) ?: throw ObjectNotFoundException(name, key)

    /** What the store knows about [key] without sending its bytes, or null if it is not there. */
    suspend fun stat(key: String): ObjectInfo? =
        withContext(Dispatchers.IO) {
            absentAsNull(NO_SUCH_KEY) {
                client
                    .statObject(
                        StatObjectArgs
                            .builder()
                            .bucket(name)
                            .`object`(key)
                            .build(),
                    ).await()
            }?.let {
                ObjectInfo(key, it.size(), it.lastModified()?.toInstant()?.toKotlinInstant(), it.etag(), it.contentType())
            }
        }

    suspend fun exists(key: String): Boolean = stat(key) != null

    /**
     * Every object under [prefix], as it is listed.
     *
     * A `Flow` and not a `List` because a bucket has no size limit and the store pages the answer
     * anyway: a caller that wants the first ten stops after ten, rather than after all of them.
     *
     * With [recursive] false the store collapses everything below the next `/` into one entry, the
     * way a directory listing would — S3 has no directories, only keys with slashes in them, and
     * this is the illusion it offers.
     */
    fun list(
        prefix: String = "",
        recursive: Boolean = true,
    ): Flow<ObjectInfo> =
        flow {
            val results =
                client.listObjects(
                    ListObjectsArgs
                        .builder()
                        .bucket(name)
                        .prefix(prefix)
                        .recursive(recursive)
                        .build(),
                )
            results.forEach { result ->
                val item = result.get()
                emit(ObjectInfo(item.objectName(), item.size(), item.lastModified()?.toInstant()?.toKotlinInstant(), item.etag()))
            }
        }.flowOn(Dispatchers.IO)

    // ─── Deletion ─────────────────────────────────────────────────────────────

    /** Deleting an object that is not there is not an error, in S3 or here. */
    suspend fun delete(key: String) {
        withContext(Dispatchers.IO) {
            client
                .removeObject(
                    RemoveObjectArgs
                        .builder()
                        .bucket(name)
                        .`object`(key)
                        .build(),
                ).await()
        }
    }

    /**
     * Deletes [keys] in one request, and answers with the ones the store refused.
     *
     * The result iterable is walked even when nobody wants the errors, and that is not tidiness:
     * the SDK's `removeObjects` is lazy, and a caller who ignores what it returns has deleted
     * nothing at all.
     */
    suspend fun deleteAll(keys: Collection<String>): List<String> {
        if (keys.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            client
                .removeObjects(
                    RemoveObjectsArgs
                        .builder()
                        .bucket(name)
                        .objects(keys.map { DeleteRequest.Object(it) })
                        .build(),
                ).map { it.get().objectName() }
        }
    }

    private companion object {
        /** The SDK's minimum part size, used only when the stream's length is unknown. */
        const val PART_SIZE = 5L * 1024 * 1024
    }
}
