package com.strange.storage.presign

import com.strange.storage.bucket.StorageBucket
import io.minio.PostPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * A form a browser can post an upload to: where it goes, and the hidden fields that must go with it.
 *
 * [fields] are sent as form parts *before* the file part — S3 reads the policy and signature on the
 * way past and rejects the upload the moment they do not match, rather than after receiving it all.
 */
data class PresignedPost(
    val url: String,
    val fields: Map<String, String>,
)

/**
 * A signed upload form for [key], valid until it expires.
 *
 * This is the form to reach for when the upload comes from a browser and its terms matter:
 * [sizeRange] and [contentType] become conditions inside the signed policy, so an upload that
 * breaks them is refused by the store. A [presignedPut] URL cannot carry either — anyone holding
 * one can send a gigabyte of anything.
 *
 * The URL is the endpoint with the bucket as its first path segment, which is how MinIO and any
 * path-style S3 endpoint address a bucket. A deployment behind virtual-host-style addressing
 * (`bucket.s3.region.amazonaws.com`) needs that URL built from its own hostname instead.
 */
suspend fun StorageBucket.presignedPost(
    key: String,
    expiry: Duration = DEFAULT_EXPIRY,
    sizeRange: LongRange? = null,
    contentType: String? = null,
): PresignedPost {
    requireValidExpiry(expiry)
    val deadline = Instant.fromEpochMilliseconds(System.currentTimeMillis()) + expiry
    val policy =
        PostPolicy(name, ZonedDateTime.ofInstant(deadline.toJavaInstant(), ZoneOffset.UTC)).apply {
            addEqualsCondition("key", key)
            contentType?.let { addEqualsCondition("Content-Type", it) }
            sizeRange?.let { addContentLengthRangeCondition(it.first, it.last) }
        }

    val fields =
        withContext(Dispatchers.IO) {
            client.getPresignedPostFormData(policy)
        }

    return PresignedPost(
        url = "$endpoint/$name",
        fields =
            buildMap {
                putAll(fields)
                put("key", key)
                contentType?.let { put("Content-Type", it) }
            },
    )
}
