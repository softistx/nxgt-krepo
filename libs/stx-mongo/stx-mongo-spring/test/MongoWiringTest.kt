package com.softistx.mongo.spring

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * What this auto-configuration does and, mostly, does not do.
 *
 * No server: the driver's client is lazy about its first connection, which is what lets a wiring
 * spec assert the wiring — and is also why a wrong password surfaces on first use rather than at
 * startup.
 */
class MongoWiringTest :
    StringSpec({

        "nothing is opened until an application asks" {
            runner().run { context -> context.getBeanNamesForType(MongoClient::class.java).size shouldBe 0 }
        }

        "enabling it gives a client and a database" {
            runner()
                .withPropertyValues(
                    "stx.mongo.enabled=true",
                    "stx.mongo.uri=$UNREACHABLE_MONGO",
                    "stx.mongo.database=orders",
                ).run { context ->
                    context.getBeanNamesForType(MongoClient::class.java).size shouldBe 1
                    context.getBean(MongoDatabase::class.java).name shouldBe "orders"
                }
        }

        "enabling it without a uri says which key is missing" {
            // The alternative is a binder error naming a constructor parameter, which is not where
            // the reader has to look.
            runner()
                .withPropertyValues("stx.mongo.enabled=true", "stx.mongo.database=orders")
                .run { context -> context.failure() shouldContain "stx.mongo.uri" }
        }

        "an application's own client wins" {
            // Which is how TLS, pool sizes and read concerns get set: this module has no opinion
            // about them and should not grow properties for each one.
            runner()
                .withPropertyValues(
                    "stx.mongo.enabled=true",
                    "stx.mongo.uri=$UNREACHABLE_MONGO",
                    "stx.mongo.database=orders",
                ).withUserConfiguration(OwnMongoClient::class.java)
                .run { context ->
                    context.getBeanNamesForType(MongoClient::class.java).toList() shouldBe listOf("ownClient")
                    // The database is still built over it, rather than opening a second pool.
                    context.getBean(MongoDatabase::class.java).name shouldBe "orders"
                }
        }
    })

/**
 * A Mongo address nothing listens on.
 *
 * Not `localhost:27017`, which is the workspace's own replica set: a wiring spec that points at a
 * server somebody else is running is one library change away from writing to it.
 */
private const val UNREACHABLE_MONGO = "mongodb://127.0.0.1:1"

private fun runner() =
    ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MongoIntegrationAutoConfiguration::class.java))

/** The message of whatever stopped the context starting, with its causes — the `require` is a cause. */
private fun AssertableApplicationContext.failure(): String =
    generateSequence(startupFailure) { it.cause }
        .mapNotNull { it.message }
        .joinToString("\n")

@Configuration(proxyBeanMethods = false)
private class OwnMongoClient {
    @Bean
    fun ownClient(): MongoClient = MongoClient.create(UNREACHABLE_MONGO)
}
