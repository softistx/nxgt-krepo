package com.strange.spring.integration

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.strange.jpa.Jpa
import com.strange.spring.integration.jpa.JpaIntegrationAutoConfiguration
import com.strange.spring.integration.mongo.MongoIntegrationAutoConfiguration
import com.strange.spring.integration.storage.StorageIntegrationAutoConfiguration
import com.strange.spring.integration.storage.StorageIntegrationProperties
import com.strange.storage.ObjectStorage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * What the `integration/` auto-configurations do and, mostly, do not do.
 *
 * Every one of the seven is the same shape — `@ConditionalOnClass`, `@ConditionalOnProperty` with no
 * `matchIfMissing`, `@ConditionalOnMissingBean` on every bean, built through that library's own
 * factory — so the specs are the same three questions asked of each: nothing without the property,
 * the beans with it, and an application's own bean winning.
 *
 * None of these connect to anything. Every client here is lazy about its first connection, which is
 * what lets a wiring spec assert the wiring without a server — and is also why a wrong password
 * surfaces on first use rather than at startup.
 */
class IntegrationWiringTest :
    StringSpec({

        "mongo: nothing is opened until an application asks" {
            mongo().run { context ->
                context.getBeanNamesForType(MongoClient::class.java).size shouldBe 0
            }
        }

        "mongo: enabling it gives a client and a database" {
            mongo()
                .withPropertyValues(
                    "stx.mongo.enabled=true",
                    "stx.mongo.uri=mongodb://localhost:27017",
                    "stx.mongo.database=orders",
                ).run { context ->
                    context.getBeanNamesForType(MongoClient::class.java).size shouldBe 1
                    context.getBean(MongoDatabase::class.java).name shouldBe "orders"
                }
        }

        "mongo: enabling it without a uri says which key is missing" {
            // The alternative is a binder error naming a constructor parameter, which is not where
            // the reader has to look.
            mongo()
                .withPropertyValues("stx.mongo.enabled=true", "stx.mongo.database=orders")
                .run { context -> context.failure() shouldContain "stx.mongo.uri" }
        }

        "mongo: an application's own client wins" {
            // Which is how TLS, pool sizes and read concerns get set: this module has no opinion
            // about them and should not grow properties for each one.
            mongo()
                .withPropertyValues(
                    "stx.mongo.enabled=true",
                    "stx.mongo.uri=mongodb://localhost:27017",
                    "stx.mongo.database=orders",
                ).withUserConfiguration(OwnMongoClient::class.java)
                .run { context ->
                    context.getBeanNamesForType(MongoClient::class.java).toList() shouldBe listOf("ownClient")
                    // The database is still built over it, rather than opening a second pool.
                    context.getBean(MongoDatabase::class.java).name shouldBe "orders"
                }
        }

        "jpa: nothing is built until an application asks" {
            jpa().run { context -> context.getBeanNamesForType(Jpa::class.java).size shouldBe 0 }
        }

        "jpa: enabling it without packages says so" {
            // Naming no packages would build a session factory that maps nothing, and the first
            // query would fail with an unrelated message about an unknown entity.
            jpa()
                .withPropertyValues("stx.jpa.enabled=true")
                .run { context -> context.failure() shouldContain "stx.jpa.packages" }
        }

        "storage: nothing is opened until an application asks" {
            storage().run { context -> context.getBeanNamesForType(ObjectStorage::class.java).size shouldBe 0 }
        }

        "storage: enabling it opens a client" {
            storage()
                .withPropertyValues(
                    "stx.storage.enabled=true",
                    "stx.storage.endpoint=http://localhost:9000",
                    "stx.storage.access-key=who",
                    "stx.storage.secret-key=cares",
                ).run { context -> context.getBeanNamesForType(ObjectStorage::class.java).size shouldBe 1 }
        }

        "storage: a missing credential names itself, and none of them has a default" {
            // A credential with a default is a credential in source control.
            storage()
                .withPropertyValues("stx.storage.enabled=true", "stx.storage.endpoint=http://localhost:9000")
                .run { context -> context.failure() shouldContain "stx.storage.access-key" }

            with(StorageIntegrationProperties()) {
                accessKey shouldBe null
                secretKey shouldBe null
            }
        }
    })

private fun mongo() = runnerFor(MongoIntegrationAutoConfiguration::class.java)

private fun jpa() = runnerFor(JpaIntegrationAutoConfiguration::class.java)

private fun storage() = runnerFor(StorageIntegrationAutoConfiguration::class.java)

private fun runnerFor(type: Class<*>) = ApplicationContextRunner().withConfiguration(AutoConfigurations.of(type))

/** The message of whatever stopped the context starting, with its causes — the `require` is a cause. */
private fun AssertableApplicationContext.failure(): String =
    generateSequence(startupFailure) { it.cause }
        .mapNotNull { it.message }
        .joinToString("\n")

@Configuration(proxyBeanMethods = false)
private class OwnMongoClient {
    @Bean
    fun ownClient(): MongoClient = MongoClient.create("mongodb://localhost:27017")
}
