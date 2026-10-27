package com.softistx.storage.bucket

import com.softistx.storage.NO_SUCH_KEY
import com.softistx.storage.absentAsNull
import io.minio.GetObjectArgs
import io.minio.ListObjectsArgs
import io.minio.StatObjectArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.time.toKotlinInstant

/** The ranged read behind [StorageBucket.read]: only `NoSuchKey` becomes null. */
internal suspend fun <T> StorageBucket.readObject(
    key: String,
    offset: Long?,
    length: Long?,
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

/** The metadata lookup behind [StorageBucket.stat]. */
internal suspend fun StorageBucket.statObject(key: String): ObjectInfo? =
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

/** The listing behind [StorageBucket.list]; the SDK's iterable does network I/O as it is walked. */
internal fun StorageBucket.listObjects(
    prefix: String,
    recursive: Boolean,
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
