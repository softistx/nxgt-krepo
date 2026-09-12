package com.softistx.storage

/**
 * Where the object store is, and who this application is on it.
 *
 * [accessKey] and [secretKey] have no defaults on purpose: a credential with a default is a
 * credential in source control, and the one place it is guaranteed not to belong. They come from
 * the environment, a secret manager, or whatever the deployment already uses for the rest.
 *
 * [region] can stay null against MinIO, which does not care, and usually cannot against S3 itself —
 * the SDK will otherwise ask the service where a bucket lives, which is a round trip per bucket the
 * first time it sees one.
 */
data class StorageConfig(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val region: String? = null,
)
