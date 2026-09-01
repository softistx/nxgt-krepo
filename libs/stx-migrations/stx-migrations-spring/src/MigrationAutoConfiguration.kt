package com.softistx.migrations.spring

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.jpa.Jpa
import com.softistx.migrations.MigrationRunner
import com.softistx.migrations.db.mongo.MongoMigration
import com.softistx.migrations.db.mongo.MongoMigrationLedger
import com.softistx.migrations.db.mongo.MongoMigrations
import com.softistx.migrations.db.sql.SqlMigration
import com.softistx.migrations.db.sql.SqlMigrationLedger
import com.softistx.migrations.db.sql.SqlMigrations
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

/**
 * Runs `stx-migrations` before the application finishes starting.
 *
 * ```yaml
 * stx:
 *   jpa: { enabled: true, uri: postgresql://localhost:5432/orders, username: …, password: … }
 *   migrations: { enabled: true, store: sql }
 * ```
 *
 * ```kotlin
 * @Component
 * class V1Orders : SqlMigration {
 *     override val version = 1L
 *     override suspend fun migrate(context: SqlMigrationSession) {
 *         context.execute("create table if not exists orders (id bigint primary key)")
 *     }
 * }
 * ```
 *
 * **Migrations are collected by type**, through `ObjectProvider<SqlMigration>` and
 * `ObjectProvider<MongoMigration>`. By type rather than by annotation, which is the correction the
 * runner this replaces already carried: the version before it *"filtered on the annotation and
 * silently ignored anything without it, which is a migration that does not happen and does not say
 * so."* A migration is registered by existing as a bean.
 *
 * **`stx.migrations.store` says which schema, and nothing is guessed.** An application with both a
 * `Jpa` and a `MongoDatabase` bean is not saying which one it means. An application migrating both
 * names one here and declares a `MigrationRunner` bean for the other — [MigrationGate] runs every
 * runner it finds.
 */
@AutoConfiguration
@EnableConfigurationProperties(MigrationProperties::class)
@ConditionalOnClass(MigrationRunner::class)
@ConditionalOnProperty(prefix = "stx.migrations", name = ["enabled"], havingValue = "true")
class MigrationAutoConfiguration {
    /**
     * The gate itself, which exists whether or not a store was named.
     *
     * An application that declares its own `MigrationRunner` beans and no `store` still gets one, and
     * an application that declares neither gets an empty one rather than a failure — turning
     * `stx.migrations` on before writing the first migration should not be an error.
     */
    @Bean
    @ConditionalOnMissingBean
    fun stxMigrationGate(runners: ObjectProvider<MigrationRunner<*>>): MigrationGate = MigrationGate(runners)

    /**
     * One nested configuration per store, each `@ConditionalOnClass` and each named by
     * `stx.migrations.store`.
     *
     * Nested so the enclosing configuration can be read without `stx-migrations-db` on the classpath:
     * a method signature naming a missing class is a `NoClassDefFoundError` at context refresh, and
     * the condition on the outer class is evaluated too late to prevent it.
     *
     * Neither carries a `@ConditionalOnBean` on its connection. Asking for `store: mongo` without a
     * `MongoDatabase` bean is a mistake worth an error at startup, not a context that comes up
     * quietly with no migrations and a schema nobody made.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MongoMigrationLedger::class)
    @ConditionalOnProperty(prefix = "stx.migrations", name = ["store"], havingValue = "mongo")
    class MongoLedger {
        @Bean
        @ConditionalOnMissingBean(name = ["stxMigrationRunner"])
        fun stxMigrationRunner(
            database: MongoDatabase,
            migrations: ObjectProvider<MongoMigration>,
            properties: MigrationProperties,
        ): MigrationRunner<MongoDatabase> =
            MongoMigrations(
                database = database,
                migrations = migrations.orderedStream().toList(),
                collection = properties.name,
                lease = properties.lease.or(LEASE),
                lockTimeout = properties.lockTimeout.or(LOCK_TIMEOUT),
                lockPoll = properties.lockPoll.or(LOCK_POLL),
                staleAfter = properties.staleAfter.or(STALE_AFTER),
            )
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SqlMigrationLedger::class)
    @ConditionalOnProperty(prefix = "stx.migrations", name = ["store"], havingValue = "sql")
    class SqlLedger {
        /**
         * `stx.jpa.pool-size` must be at least two, and `SqlMigrations` refuses to be built
         * otherwise: a run holds one connection for the migration and needs a second for the lease
         * watchdog. The default is ten, so this only bites an application that turned it down.
         */
        @Bean
        @ConditionalOnMissingBean(name = ["stxMigrationRunner"])
        fun stxMigrationRunner(
            jpa: Jpa,
            migrations: ObjectProvider<SqlMigration>,
            properties: MigrationProperties,
        ): MigrationRunner<*> =
            SqlMigrations(
                jpa = jpa,
                migrations = migrations.orderedStream().toList(),
                table = properties.name,
                lease = properties.lease.or(LEASE),
                lockTimeout = properties.lockTimeout.or(LOCK_TIMEOUT),
                lockPoll = properties.lockPoll.or(LOCK_POLL),
                staleAfter = properties.staleAfter.or(STALE_AFTER),
            )
    }
}

/** The library's own default when the property is unset, spelled once per key. */
private fun java.time.Duration?.or(fallback: Duration): Duration = this?.toKotlinDuration() ?: fallback

private val LEASE = 5.minutes
private val LOCK_TIMEOUT = 5.minutes
private val LOCK_POLL = 1.seconds
private val STALE_AFTER = 15.minutes
