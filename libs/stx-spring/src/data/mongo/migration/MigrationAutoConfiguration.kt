package com.strange.spring.data.mongo.migration

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

/**
 * What `stx.data.mongo.migration` configures.
 */
@ConfigurationProperties(prefix = "stx.data.mongo.migration")
data class MigrationProperties(
    /**
     * Runs pending migrations after the application is ready. Off unless asked for, like every
     * `stx.*` integration.
     *
     * The version this was extracted from defaulted to `true`, which meant adding the library to a
     * classpath was enough to have it start writing to the database.
     */
    val enabled: Boolean = false,
    /**
     * What a migration's class name starts with, before the order.
     *
     * `V` gives `V3Currencies`. Anything is accepted and it is matched literally — a prefix with a
     * `.` in it is a `.` and not a wildcard.
     */
    val prefix: String = "V",
    /** The collection migration records are written to. */
    val collection: String = MigrationEntry.COLLECTION,
)

/**
 * Registers the migration runner.
 *
 * Nothing here scans for [Migration] implementations — the runner asks the context for them, so
 * whatever the application's own component scan found is what runs.
 */
@AutoConfiguration
@EnableConfigurationProperties(MigrationProperties::class)
@ConditionalOnClass(ReactiveMongoTemplate::class)
@ConditionalOnProperty(prefix = "stx.data.mongo.migration", name = ["enabled"], havingValue = "true")
class MigrationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun migrationStore(
        template: ReactiveMongoTemplate,
        properties: MigrationProperties,
    ): MigrationStore = MigrationStore(template, properties.collection)

    @Bean
    @ConditionalOnMissingBean
    fun migrationRunner(
        context: ApplicationContext,
        store: MigrationStore,
        properties: MigrationProperties,
    ): MigrationRunner = MigrationRunner(context, store, properties.prefix)
}
