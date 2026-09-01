package com.softistx.spring.integration.mongo

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.mongo.mongoClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/**
 * What `stx.mongo` connects with.
 *
 * **Not `stx.data.mongo`, and not Spring Boot's `spring.mongodb`.** Those two configure Spring
 * Data's `ReactiveMongoTemplate`; this one hands the application `stx-mongo`'s coroutine client,
 * which is a different API onto the same server. An application uses one or the other, and an
 * application that somehow wants both gets two connection pools and should say so on purpose.
 */
@ConfigurationProperties(prefix = "stx.mongo")
data class MongoIntegrationProperties(
    /** Opens the client. Off unless asked for, like every `stx.*` integration. */
    val enabled: Boolean = false,
    /** The connection string. Required when enabled — a default here would be a guess about someone's cluster. */
    val uri: String? = null,
    /** The database the `MongoDatabase` bean points at. Required when enabled, for the same reason. */
    val database: String? = null,
)

/**
 * One `stx-mongo` client for the application, and one database handle over it.
 *
 * ```yaml
 * stx:
 *   mongo: { enabled: true, uri: mongodb://localhost:27017, database: orders }
 * ```
 *
 * The client comes from `stx-mongo`'s own [mongoClient] rather than being assembled here, and that
 * is the rule for all seven of these integrations: the factory knows something the caller does not.
 * Here it is the codec registry — a client built without it compiles, connects, reads, and then
 * stores an `Instant` as something nothing in that library can read back. Every step succeeds until
 * the data is already written.
 *
 * The driver's client is a pool and is thread-safe, so one is the right number. Spring closes it
 * when the context does, by the inferred `close()`, and `stx-mongo`'s clients close idempotently —
 * so an application that also closes its own is not a problem.
 *
 * Everything `stx-mongo` has no opinion about — TLS, pool sizes, read and write concerns — is set by
 * declaring a `MongoClient` bean instead. `@ConditionalOnMissingBean` then steps aside, and the
 * `MongoDatabase` here is still built over it.
 */
@AutoConfiguration
@EnableConfigurationProperties(MongoIntegrationProperties::class)
@ConditionalOnClass(MongoClient::class)
@ConditionalOnProperty(prefix = "stx.mongo", name = ["enabled"], havingValue = "true")
class MongoIntegrationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun stxMongoClient(properties: MongoIntegrationProperties): MongoClient =
        mongoClient(requireNotNull(properties.uri) { "stx.mongo.enabled is true but stx.mongo.uri is not set" })

    @Bean
    @ConditionalOnMissingBean
    fun stxMongoDatabase(
        client: MongoClient,
        properties: MongoIntegrationProperties,
    ): MongoDatabase =
        client.getDatabase(
            requireNotNull(properties.database) { "stx.mongo.enabled is true but stx.mongo.database is not set" },
        )
}
