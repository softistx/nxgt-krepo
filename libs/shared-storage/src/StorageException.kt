package com.strange.storage

import kotlin.time.Duration

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

/**
 * A presigned URL was asked to live for longer than SigV4 will sign, or for no time at all.
 * The signature carries its own lifetime, so the store — not this module — is the one that decides.
 */
class InvalidExpiryException(
    val expiry: Duration,
) : StorageException("A presigned URL must live between 1 second and 7 days, not $expiry")
