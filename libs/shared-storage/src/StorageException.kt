package com.strange.storage

/**
 * What this module throws. A MinIO `ErrorResponseException` still comes through untouched — the
 * service said something specific, and replacing it with a summary loses the part an operator needs.
 */
sealed class StorageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** No object under that key. Thrown only where the caller asked for the object, not for its absence. */
class ObjectNotFoundException(
    val bucket: String,
    val key: String,
) : StorageException("No object '$key' in bucket '$bucket'")

/** The bucket is not there, and this operation is not the one that creates it. */
class BucketNotFoundException(
    val bucket: String,
) : StorageException("No bucket '$bucket'")
