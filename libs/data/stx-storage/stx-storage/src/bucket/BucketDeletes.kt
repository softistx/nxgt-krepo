package com.softistx.storage.bucket

import io.minio.RemoveObjectArgs
import io.minio.RemoveObjectsArgs
import io.minio.messages.DeleteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext

/** The single delete behind [StorageBucket.delete]. */
internal suspend fun StorageBucket.removeObject(key: String) {
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

/** The batch delete behind [StorageBucket.deleteAll]; walking the result is what performs it. */
internal suspend fun StorageBucket.removeObjects(keys: Collection<String>): List<String> =
    withContext(Dispatchers.IO) {
        client
            .removeObjects(
                RemoveObjectsArgs
                    .builder()
                    .bucket(name)
                    .objects(keys.map { DeleteRequest.Object(it) })
                    .build(),
            ).map { it.get().objectName() }
    }
