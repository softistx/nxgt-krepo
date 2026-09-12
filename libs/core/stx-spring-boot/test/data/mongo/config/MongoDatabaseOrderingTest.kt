package com.softistx.spring.data.mongo.config

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mongodb.reactivestreams.client.MongoClients
import com.softistx.mongo.spring.MongoIntegrationAutoConfiguration
import com.softistx.spring.testing.UNREACHABLE_MONGO
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory

/**
 * Which coroutine `MongoDatabase` an application ends up with when both configurations can give it
 * one.
 *
 * Two of them can. [MongoAutoConfiguration]'s nested `CoroutineDatabaseConfiguration` infers one
 * from the pool Spring Data already has; `stx-mongo-spring` builds one from a client `stx.mongo`
 * opened itself. Both are `@ConditionalOnMissingBean`, and the loser of that race is a migration run
 * against a database nobody chose — so which one survives is worth writing down.
 *
 * **What this spec does and does not establish.** It pins the outcome: with both present and both
 * enabled, the database an application gets is the one `stx.mongo` named out loud. It does *not*
 * prove that `@AutoConfiguration(afterName = …)` is what causes that. `ApplicationContextRunner`
 * does not reproduce Boot's auto-configuration sort — measured, not assumed: the result is unchanged
 * when the two are passed to `AutoConfigurations.of` in the opposite order, and unchanged again when
 * the `afterName` string is deliberately misspelled. A real application refresh is where that
 * ordering applies, and `examples/spring-orders` is where the whole path runs.
 *
 * The first scenario is the one that guards the string itself, which is the failure a compiler would
 * have caught if this could be a class reference: the two modules may not depend on each other, so
 * the ordering names its class by name, and a rename would otherwise drop it silently.
 *
 * `stx-spring-boot`'s **test** dependencies name `stx-mongo-spring` for this file alone. Not a
 * cycle: a test edge back at a module that depends on this one's main sources, which the toolchain
 * resolves.
 */
class MongoDatabaseOrderingTest :
    StringSpec({

        "the class the ordering names is the class that exists" {
            MongoIntegrationAutoConfiguration::class.java.name shouldBe
                "com.softistx.mongo.spring.MongoIntegrationAutoConfiguration"
        }

        "with both enabled, the database is the one stx.mongo named" {
            // stx.mongo names a URI and a database out loud; stx.data.mongo only ever infers one
            // from whatever pool Spring Data was given.
            ApplicationContextRunner()
                .withConfiguration(
                    AutoConfigurations.of(
                        MongoAutoConfiguration::class.java,
                        MongoIntegrationAutoConfiguration::class.java,
                    ),
                ).withUserConfiguration(SpringDataPool::class.java)
                .withPropertyValues(
                    "stx.data.mongo.enabled=true",
                    "stx.mongo.enabled=true",
                    "stx.mongo.uri=$UNREACHABLE_MONGO",
                    "stx.mongo.database=chosen",
                ).run { context ->
                    context.getBean(MongoDatabase::class.java).name shouldBe "chosen"
                }
        }

        "and the inferred one is used when nothing else names a database" {
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MongoAutoConfiguration::class.java))
                .withUserConfiguration(SpringDataPool::class.java)
                .withPropertyValues("stx.data.mongo.enabled=true")
                .run { context ->
                    context.getBean(MongoDatabase::class.java).name shouldBe "inferred"
                }
        }
    })

/** A Spring Data pool over a server nothing listens on — the handle is all this spec needs. */
@Configuration(proxyBeanMethods = false)
private class SpringDataPool {
    @Bean
    fun factory(): ReactiveMongoDatabaseFactory = SimpleReactiveMongoDatabaseFactory(MongoClients.create(UNREACHABLE_MONGO), "inferred")
}
