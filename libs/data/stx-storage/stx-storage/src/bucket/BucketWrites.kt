package com.softistx.storage.bucket

import io.minio.CopyObjectArgs
import io.minio.PutObjectArgs
import io.minio.SourceObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.io.InputStream

/** The SDK's minimum part size, used only when the stream's length is unknown. */
private const val PART_SIZE = 5L * 1024 * 1024

/** The upload behind [StorageBucket.put]: on the IO dispatcher, because the SDK reads the stream on the caller. */
internal suspend fun StorageBucket.putObject(
    key: String,
    stream: InputStream,
    size: Long,
    contentType: String?,
    metadata: Map<String, String>,
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

/** The server-side copy behind [StorageBucket.copy]. */
internal suspend fun StorageBucket.copyObject(
    sourceKey: String,
    targetKey: String,
    targetBucket: String,
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
