package com.strange.storage.presign

import com.strange.storage.InvalidExpiryException
import com.strange.storage.bucket.StorageBucket
import io.minio.GetPresignedObjectUrlArgs
import io.minio.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What a URL gets if the caller does not say. Short, because a leaked URL is valid until it expires. */
val DEFAULT_EXPIRY: Duration = 15.minutes

/** The longest life SigV4 will sign. Asking for more is rejected by the store, so it is rejected here. */
val MAX_EXPIRY: Duration = 7.days

/**
 * A URL that downloads [key] without a credential, until it expires.
 *
 * The point is to keep the bytes out of the application: hand this to a browser and the store
 * serves the object directly, rather than the object being read here and copied through a response.
 * Nothing about the URL is checked against the bucket first — it signs a request, and a URL for an
 * object that is not there answers 404 when it is used.
 *
 * [filename] sets `Content-Disposition` so the browser saves rather than renders, and [contentType]
 * overrides what the store recorded at upload. Both travel inside the signature, so neither can be
 * changed by whoever holds the URL.
 */
suspend fun StorageBucket.presignedGet(
    key: String,
    expiry: Duration = DEFAULT_EXPIRY,
    filename: String? = null,
    contentType: String? = null,
): String =
    presigned(
        key = key,
        method = Http.Method.GET,
        expiry = expiry,
        queryParams =
            buildMap {
                filename?.let { put("response-content-disposition", "attachment; filename=\"$it\"") }
                contentType?.let { put("response-content-type", it) }
            },
    )

/**
 * A URL that uploads to [key] without a credential, until it expires.
 *
 * The holder decides what to send: the signature covers the bucket, the key and the deadline, and
 * nothing else. There is no size limit and no content-type limit on a presigned PUT — when those
 * matter, [presignedPost] is the form that can carry them.
 */
suspend fun StorageBucket.presignedPut(
    key: String,
    expiry: Duration = DEFAULT_EXPIRY,
): String = presigned(key, Http.Method.PUT, expiry)

/** A URL that deletes [key] without a credential. Rare, and worth a second thought before handing out. */
suspend fun StorageBucket.presignedDelete(
    key: String,
    expiry: Duration = DEFAULT_EXPIRY,
): String = presigned(key, Http.Method.DELETE, expiry)

/**
 * Signing is local arithmetic, but the SDK may first ask the store which region the bucket is in —
 * a blocking round trip, cached afterwards. [Dispatchers.IO] is for that first call.
 */
private suspend fun StorageBucket.presigned(
    key: String,
    method: Http.Method,
    expiry: Duration,
    queryParams: Map<String, String> = emptyMap(),
): String {
    requireValidExpiry(expiry)
    return withContext(Dispatchers.IO) {
        client.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs
                .builder()
                .bucket(name)
                .`object`(key)
                .method(method)
                .expiry(expiry.inWholeSeconds.toInt())
                .apply { if (queryParams.isNotEmpty()) extraQueryParams(queryParams) }
                .build(),
        )
    }
}

internal fun requireValidExpiry(expiry: Duration) {
    if (expiry < 1.seconds || expiry > MAX_EXPIRY) throw InvalidExpiryException(expiry)
}
