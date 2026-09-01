package com.softistx.spring.testing

import com.mongodb.ConnectionString
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails
import org.springframework.boot.mongodb.autoconfigure.MongoProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * Where MongoDB is, answered as a bean rather than as a property.
 *
 * **This is the seam Boot offers and `@DynamicPropertySource` is not.**
 * `MongoReactiveAutoConfiguration` declares its own `PropertiesMongoConnectionDetails` under
 * `@ConditionalOnMissingBean(MongoConnectionDetails::class)`, so the bean below wins outright — no
 * property registry, no ordering question, and no `@JvmStatic` companion method that every spec used
 * to have to copy. It is imported by [MongoSpec]; a spec that wants its own annotations imports it
 * directly instead of inheriting anything.
 *
 * **The database is named by `spring.mongodb.database`, and the prefix is not a typo.** Spring Boot 4
 * split the old `spring.data.mongodb` in two: `MongoProperties` — the driver's URI, host, port,
 * credentials and database — moved to `spring.mongodb`, and `spring.data.mongodb` kept only
 * `DataMongoProperties`, which is GridFS and the big-decimal representation. A `spring.data.mongodb.uri`
 * left over from Boot 3 now binds to nothing and is reported by nothing: the driver quietly uses
 * `MongoProperties.DEFAULT_URI`, `mongodb://localhost/test`, which on a developer machine is a real
 * server that answers. That is not hypothetical — it is what `examples/spring-orders` was doing until
 * this class replaced its `@DynamicPropertySource`, and its suite passed the whole time.
 *
 * That is also the argument for a bean over a property. This one is asked for by type, so it cannot
 * be misspelled, and `MongoSpecTest` pins the prefix so a future rename fails loudly instead of
 * silently pointing a suite at somebody's own MongoDB.
 *
 * An application writes one line in `testResources/application-test.yaml` and no Kotlin. What it
 * names is a *prefix*: [testDatabase] puts this run's suffix on it, so two suites against the same
 * server cannot empty each other's collections.
 *
 * **The suffix is applied to the property, not only to the connection string, and that is not
 * belt-and-braces.** `DataMongoReactiveAutoConfiguration.reactiveMongoDatabaseFactory` reads
 * `MongoProperties.getDatabase()` *first* and only falls back to the connection string's database
 * when it is null — so contributing the name through the URI alone leaves the driver connected to one
 * database and `ReactiveMongoTemplate` reading another. That is not a guess: the first run of this
 * with the suffix in the URI alone failed `MongoSpecTest` with `expected:<…_d6yhy7pd> but
 * was:<stx_spring_boot_test>`, and both scenarios are still there so the two can never drift apart
 * again. The name goes into the connection string as well, for [withDatabase]'s reason — an endpoint
 * that already ends in a database would otherwise decide where an unqualified operation lands.
 *
 * The database is dropped when the run ends — see [MongoTestCleanup] for why that is a shutdown hook
 * and not a bean's destroy method.
 *
 * **An unreachable URI is deliberate when there is no server.** Contributing nothing would leave
 * `application.yaml`'s real URI in place and point a suite at the database a `./kotlin run` writes to.
 * A port nothing listens on lets the context start and every feature gated on [mongoAvailable] report
 * skipped, which is the outcome a machine without Docker should get.
 */
@TestConfiguration(proxyBeanMethods = false)
class MongoTestConfiguration {
    @Bean
    fun mongoConnectionDetails(properties: MongoProperties): MongoConnectionDetails =
        MongoConnectionDetails {
            val database = properties.database ?: DEFAULT_DATABASE
            val uri = TestMongo.service.endpoint?.withDatabase(database) ?: "$UNREACHABLE_MONGO/$database"
            MongoTestCleanup.dropAtExit(uri, database)
            ConnectionString(uri)
        }

    companion object {
        /** The prefix used when an application named no database. Generic, and so worth not relying on. */
        private const val DEFAULT_DATABASE = "stx_test"

        /**
         * Renames the bound database to this run's, before anything has read it.
         *
         * A post-processor rather than a side effect in [mongoConnectionDetails], because the order
         * in which two `@Bean` methods read a shared properties object is not something a test
         * harness should have to be right about. This runs when `MongoProperties` is initialised,
         * which is before any bean can be injected with it.
         *
         * `@JvmStatic` is required, not stylistic: a `BeanPostProcessor` returned from an instance
         * method forces its configuration class to be instantiated ahead of the post-processor
         * registry, and Spring warns that the class is then not eligible for processing by all of it.
         */
        @Bean
        @JvmStatic
        fun testDatabaseNaming(): BeanPostProcessor =
            object : BeanPostProcessor {
                override fun postProcessAfterInitialization(
                    bean: Any,
                    beanName: String,
                ): Any =
                    bean.also {
                        if (it is MongoProperties) it.database = testDatabase(it.database ?: DEFAULT_DATABASE)
                    }
            }
    }
}
