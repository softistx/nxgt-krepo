package com.softistx.storage.spring

import com.softistx.storage.ObjectStorage
import com.softistx.storage.StorageConfig
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/**
 * What `stx.storage` connects with.
 *
 * **[accessKey] and [secretKey] are nullable and have no default.** A credential with a default is a
 * credential in source control. They are nullable rather than required by the binder so that the
 * failure is a sentence naming the missing key, rather than a binding error naming a constructor
 * parameter — the same reason `StorageConfig` leaves them without defaults.
 */
@ConfigurationProperties(prefix = "stx.storage")
data class StorageIntegrationProperties(
    /** Opens the client. Off unless asked for. */
    val enabled: Boolean = false,
    /** Where the store is. MinIO, S3, or anything speaking S3. */
    val endpoint: String? = null,
    val accessKey: String? = null,
    val secretKey: String? = null,
    /**
     * The region.
     *
     * Can stay null against MinIO, which does not care. Against S3 itself it usually cannot: the SDK
     * will otherwise ask the service where a bucket lives, once per bucket the first time it sees one.
     */
    val region: String? = null,
)

/**
 * One `stx-storage` connection for the application.
 *
 * ```yaml
 * stx:
 *   storage:
 *     enabled: true
 *     endpoint: http://localhost:9000
 *     access-key: ${MINIO_ACCESS_KEY}
 *     secret-key: ${MINIO_SECRET_KEY}
 * ```
 *
 * One client is the right number — it owns an OkHttp connection pool and is thread-safe. Spring
 * closes it with the context through the inferred `close()`, which is idempotent via `CloseGuard`.
 * Worth knowing: closing the client shuts that pool down, so a download still being read when the
 * context closes goes with it.
 *
 * **No buckets are created here.** `ObjectStorage.ensureBucket` is one call and belongs to whoever
 * knows which buckets this application needs; creating them from a property list would make a
 * startup that writes to somebody's object store out of a config file nobody reviewed as a schema.
 */
@AutoConfiguration
@EnableConfigurationProperties(StorageIntegrationProperties::class)
@ConditionalOnClass(ObjectStorage::class)
@ConditionalOnProperty(prefix = "stx.storage", name = ["enabled"], havingValue = "true")
class StorageIntegrationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun stxObjectStorage(properties: StorageIntegrationProperties): ObjectStorage =
        ObjectStorage.connect(
            StorageConfig(
                endpoint = properties.endpoint.required("endpoint"),
                accessKey = properties.accessKey.required("access-key"),
                secretKey = properties.secretKey.required("secret-key"),
                region = properties.region,
            ),
        )
}

/** Names the missing key, rather than leaving the binder to name a constructor parameter. */
private fun String?.required(key: String): String = requireNotNull(this) { "stx.storage.enabled is true but stx.storage.$key is not set" }
