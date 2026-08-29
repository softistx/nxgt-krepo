package com.strange.spring.data.mongo.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * What `stx.data.mongo` configures.
 *
 * ```yaml
 * stx:
 *   data:
 *     mongo:
 *       enabled: true
 *       gridfs-bucket: uploads
 * ```
 *
 * `stx.data.mongo` and not `stx.mongo`: this is the Spring Data layer, and `stx.mongo` belongs to
 * the `stx-mongo` library's own integration — two layers over the same driver, and an application
 * may reasonably use either.
 *
 * Where a switch here overlaps one of Spring Boot's, Boot's stays in charge. This adds what Boot has
 * no opinion about, and does not re-express what it already configures.
 */
@ConfigurationProperties(prefix = "stx.data.mongo")
data class MongoProperties(
    /** Registers everything below. Off unless asked for, like every `stx.*` integration. */
    val enabled: Boolean = false,
    /**
     * The GridFS bucket a `ReactiveGridFsTemplate` is opened on, or null for no template at all.
     *
     * Null rather than a default name, because a bucket that appears because nobody set a property
     * is two collections in a database that never asked for them.
     */
    val gridfsBucket: String? = null,
    /**
     * Registers a `ReactiveMongoTransactionManager`.
     *
     * Off, and worth knowing why before turning it on: Mongo transactions need a replica set, and a
     * standalone `mongod` fails the first `startTransaction` rather than at startup — so this
     * turned on against the wrong server is a failure at the first write, not at boot.
     */
    val transactions: Boolean = false,
    /**
     * Registers a `ReactiveAuditorAware` that answers with the authenticated user's name, so
     * `@CreatedBy` and `@LastModifiedBy` fill themselves in.
     *
     * Needs Spring Security on the classpath and `@EnableReactiveMongoAuditing` in the application —
     * enabling auditing changes how every entity is persisted, which is the application's call.
     */
    val auditing: Boolean = false,
    /**
     * Creates the indexes the mapped entities declare, once, after the application is ready.
     *
     * Creating an index that already exists is a no-op in Mongo, so this is safe on every boot.
     * Nothing is ever dropped — see `IndexInitializer` for why that matters more than it sounds.
     */
    val createIndexes: Boolean = false,
)
