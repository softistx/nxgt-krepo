package com.softistx.storage

import io.minio.errors.ErrorResponseException

/**
 * The S3 error code behind a failure, or null if it was not an S3 error at all.
 *
 * Every "is this just missing?" question in this module goes through here rather than through
 * `catch (e: Exception) { null }`. The two are indistinguishable at the call site and wildly
 * different in production: one is an object that was never uploaded, the other is a credential that
 * expired, and swallowing the second as an absence turns an outage into an empty page.
 */
internal val Throwable.s3ErrorCode: String?
    get() = (this as? ErrorResponseException)?.errorResponse()?.code()

internal const val NO_SUCH_KEY = "NoSuchKey"
internal const val NO_SUCH_BUCKET = "NoSuchBucket"
internal const val BUCKET_ALREADY_OWNED = "BucketAlreadyOwnedByYou"

/** Runs [block], answering null when S3 says the thing is simply not there. */
internal inline fun <T> absentAsNull(
    vararg codes: String,
    block: () -> T,
): T? =
    try {
        block()
    } catch (e: ErrorResponseException) {
        if (e.s3ErrorCode in codes) null else throw e
    }
