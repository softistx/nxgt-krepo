package com.strange.spring.data.mongo.config

import com.strange.spring.data.mongo.convert.stxMongoConverters
import kotlinx.coroutines.reactor.mono
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.gridfs.ReactiveGridFsTemplate

/**
 * What `stx.data.mongo` registers.
 *
 * **Ordered before Spring Boot's own Mongo configuration, and that is the whole trick.** Boot's
 * `MongoCustomConversions` bean is `@ConditionalOnMissingBean`, so a library contributing one after
 * it never applies, and one contributing it *without* ordering replaces Boot's — quietly dropping
 * `spring.data.mongodb.representation`. Registering first and carrying that property across is the
 * only arrangement where both the `kotlin.time.Instant` converters and Boot's own setting survive.
 */
@AutoConfiguration(before = [DataMongoAutoConfiguration::class])
@EnableConfigurationProperties(MongoProperties::class, DataMongoProperties::class)
@ConditionalOnClass(ReactiveMongoTemplate::class)
@ConditionalOnProperty(prefix = "stx.data.mongo", name = ["enabled"], havingValue = "true")
class MongoAutoConfiguration {
    /**
     * The conversions, with this module's converters added to whatever Boot would have configured.
     *
     * Without the `Instant` converters an entity holding a `kotlin.time.Instant` fails at *query*
     * time with `Can't find a codec`, not at mapping time — so this is the difference between a
     * field that works and one that looks fine until something reads it.
     */
    @Bean
    @ConditionalOnMissingBean
    fun mongoCustomConversions(boot: DataMongoProperties): MongoCustomConversions =
        MongoCustomConversions.create { adapter ->
            adapter.registerConverters(stxMongoConverters())
            // Boot's own property, applied here because this bean is standing in its place.
            boot.representation.bigDecimal?.let(adapter::bigDecimal)
        }

    /**
     * A GridFS template on the configured bucket.
     *
     * Only when a bucket is named: one appearing because nobody set a property is two collections in
     * a database that never asked for them.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "stx.data.mongo", name = ["gridfs-bucket"])
    fun reactiveGridFsTemplate(
        factory: ReactiveMongoDatabaseFactory,
        converter: MappingMongoConverter,
        properties: MongoProperties,
    ): ReactiveGridFsTemplate = ReactiveGridFsTemplate(factory, converter, properties.gridfsBucket)

    /**
     * Transactions, which need a replica set.
     *
     * A standalone `mongod` fails the first `startTransaction` rather than at startup, so this
     * turned on against the wrong server is a failure at the first write and not at boot.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "stx.data.mongo", name = ["transactions"], havingValue = "true")
    fun reactiveMongoTransactionManager(factory: ReactiveMongoDatabaseFactory): ReactiveMongoTransactionManager =
        ReactiveMongoTransactionManager(factory)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "stx.data.mongo", name = ["create-indexes"], havingValue = "true")
    fun stxIndexInitializer(template: ReactiveMongoTemplate): IndexInitializer = IndexInitializer(template)

    /**
     * Nested so `@ConditionalOnClass` applies to this bean alone. Spring Security is `compile-only`
     * here, and gating the whole configuration on it would leave an application without security
     * getting no conversions either — which is the bean that actually matters.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.security.core.context.ReactiveSecurityContextHolder"])
    @ConditionalOnProperty(prefix = "stx.data.mongo", name = ["auditing"], havingValue = "true")
    class AuditorConfiguration {
        /**
         * Who `@CreatedBy` and `@LastModifiedBy` are filled in with.
         *
         * Reads the *reactive* security context, for the reason `currentUser()` gives: in WebFlux a
         * request is not a thread, and a `ThreadLocal` here would stamp documents with whoever last
         * used the worker.
         *
         * `@EnableReactiveMongoAuditing` stays the application's to add — it changes how every
         * entity is persisted.
         */
        @Bean
        @ConditionalOnMissingBean
        fun stxAuditorAware(): org.springframework.data.domain.ReactiveAuditorAware<String> =
            org.springframework.data.domain.ReactiveAuditorAware {
                mono {
                    com.strange.spring.security
                        .currentUser()
                        ?.username
                }
            }
    }
}
