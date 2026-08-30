package com.strange.spring.testing

import com.mongodb.ConnectionString
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
 * An application writes one line in `testResources/application-test.yaml` and no Kotlin. The name is
 * spliced into the connection string here — see [withDatabase] for why it goes there rather than
 * being left to the auto-configuration to apply.
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
            ConnectionString(TestMongo.service.endpoint?.withDatabase(database) ?: "$UNREACHABLE/$database")
        }

    private companion object {
        /** Used when an application named no database. Shared, and so worth not relying on. */
        const val DEFAULT_DATABASE = "stx_test"

        /** A port nothing listens on, reached only where there is neither Docker nor `MONGO_TEST_URI`. */
        const val UNREACHABLE = "mongodb://127.0.0.1:1"
    }
}
