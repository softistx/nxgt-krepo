package com.softistx.spring.data.mongo.config

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.spring.data.mongo.convert.stxMongoConverters
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
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
 *
 * The `afterName` is a second ordering question, and it decides a winner rather than a moment.
 * `stx-mongo-spring`'s `MongoIntegrationAutoConfiguration` contributes a coroutine `MongoDatabase`
 * too, from a client `stx.mongo` opened itself; so does [CoroutineDatabaseConfiguration] below, from
 * the pool Spring Data already has. Both are `@ConditionalOnMissingBean`, so without an order the
 * winner in an application that turned on both would be whichever configuration Boot happened to
 * read first — and the loser is a migration run against a database nobody chose. Ordered, an
 * explicit `stx.mongo` wins: it names a URI and a database out loud, and this one only ever infers.
 *
 * **A name rather than a class, and that is the point of the string.** That configuration lives in
 * `stx-mongo-spring` now, which depends on nothing here — so naming its class would mean this module
 * depending on it, and the edge would point the wrong way for the sake of an ordering hint. Spring
 * resolves `afterName` lazily and ignores what it cannot find, so an application without
 * `stx-mongo-spring` on its classpath is not asked to have it.
 *
 * What a string costs is that the compiler cannot check it, so `MongoDatabaseOrderingTest` checks
 * the name — and says plainly what it cannot check, because `ApplicationContextRunner` does not
 * reproduce Boot's auto-configuration sort.
 */
@AutoConfiguration(
    before = [DataMongoAutoConfiguration::class],
    afterName = ["com.softistx.mongo.spring.MongoIntegrationAutoConfiguration"],
)
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
     * Spring Data's database, handed over as the coroutine driver's [MongoDatabase].
     *
     * The bridge `stx-migrations-spring` needs and that neither library should have to know about:
     * `stx.migrations.store: mongo` asks the context for a `MongoDatabase` bean, and an application
     * on Spring Data has a `ReactiveMongoDatabaseFactory` instead.
     *
     * **Turning on `stx.mongo` to get one is the wrong answer, not a longer one.** That opens a
     * second pool against the same server, and in a suite it opens one that never saw the per-run
     * database suffix `MongoTestConfiguration` splices into `MongoProperties` — so the migrations
     * would run against a database nobody chose, and every spec would still pass. Building this from
     * the *factory* is what makes the suffix arrive: the factory is the bean that post-processor has
     * already been through.
     *
     * It wraps the pool Spring Data opened and owns none of it. The coroutine `MongoDatabase` has no
     * `close()`, so there is nothing for Spring to infer a destroy method from and the client stays
     * Spring Data's to close — the same rule as every `stx-ktor` integration: close only what you
     * opened.
     *
     * `runBlocking` because a `@Bean` method cannot suspend. What it waits for is a handle and not a
     * round trip: `getMongoDatabase()` returns a `Mono` the factory completes from what it is
     * already holding.
     *
     * Nested so `@ConditionalOnClass` applies to this bean alone — the coroutine driver arrives with
     * `stx-mongo`, which is `compile-only` here, and a method signature naming a class that is not
     * on the classpath is a `NoClassDefFoundError` at refresh, too early for a condition on the
     * enclosing class to prevent.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MongoDatabase::class)
    class CoroutineDatabaseConfiguration {
        @Bean
        @ConditionalOnMissingBean
        fun stxCoroutineMongoDatabase(factory: ReactiveMongoDatabaseFactory): MongoDatabase =
            MongoDatabase(runBlocking { factory.mongoDatabase.awaitSingle() })
    }

    /**
     * Nested so `@ConditionalOnClass` applies to this bean alone. Spring Security is `compile-only`
     * here, and gating the whole configuration on it would leave an application without security
     * getting no conversions either — which is the bean that actually matters.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.security.core.context.ReactiveSecurityContextHolder"])
    @ConditionalOnProperty(prefix = "stx.data.mongo", name = ["auditor"], havingValue = "true")
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
                    com.softistx.spring.security
                        .currentUser()
                        ?.username
                }
            }
    }
}
