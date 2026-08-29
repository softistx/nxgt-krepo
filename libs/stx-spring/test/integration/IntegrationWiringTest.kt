package com.strange.spring.integration

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.strange.amqp.Amqp
import com.strange.i18n.Messages
import com.strange.jpa.Jpa
import com.strange.kafka.Kafka
import com.strange.redis.Redis
import com.strange.spring.integration.amqp.AmqpIntegrationAutoConfiguration
import com.strange.spring.integration.i18n.I18nIntegrationAutoConfiguration
import com.strange.spring.integration.jpa.JpaIntegrationAutoConfiguration
import com.strange.spring.integration.kafka.KafkaIntegrationAutoConfiguration
import com.strange.spring.integration.mongo.MongoIntegrationAutoConfiguration
import com.strange.spring.integration.redis.RedisIntegrationAutoConfiguration
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
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver
import org.springframework.web.server.i18n.LocaleContextResolver
import java.util.Locale

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

        "kafka: nothing is registered until an application asks" {
            kafka().run { context -> context.getBeanNamesForType(Kafka::class.java).size shouldBe 0 }
        }

        "kafka: enabling it registers a cluster handle that has opened nothing" {
            // The one integration where the context is not holding a connection: a Kafka client
            // connects when it is constructed, so the connections belong to the publishers and
            // subscribers this hands out, each closed by whoever asked for it.
            kafka()
                .withPropertyValues(
                    "stx.kafka.enabled=true",
                    "stx.kafka.bootstrap=localhost:9092",
                    "stx.kafka.client-id=orders",
                ).run { context ->
                    val cluster = context.getBean(Kafka::class.java)
                    cluster.bootstrap shouldBe "localhost:9092"
                    cluster.config.clientId shouldBe "orders"
                }
        }

        "redis: nothing is opened until an application asks" {
            redis().run { context -> context.getBeanNamesForType(Redis::class.java).size shouldBe 0 }
        }

        "redis: enabling it opens a connection".config(enabled = REDIS != null) {
            // Gated on a server that is already up rather than on one this run starts: the
            // workspace's backends do not all fit at once.
            redis()
                .withPropertyValues("stx.redis.enabled=true", "stx.redis.uri=$REDIS", "stx.redis.namespace=orders")
                .run { context -> context.getBean(Redis::class.java).namespace shouldBe "orders" }
        }

        "amqp: nothing is opened until an application asks" {
            amqp().run { context -> context.getBeanNamesForType(Amqp::class.java).size shouldBe 0 }
        }

        "amqp: enabling it opens a connection".config(enabled = AMQP != null) {
            amqp()
                .withPropertyValues("stx.amqp.enabled=true", "stx.amqp.uri=$AMQP", "stx.amqp.connection-name=orders")
                .run { context -> context.getBeanNamesForType(Amqp::class.java).size shouldBe 1 }
        }

        "i18n: no catalogs are loaded until an application asks" {
            i18n().run { context -> context.getBeanNamesForType(Messages::class.java).size shouldBe 0 }
        }

        "i18n: enabling it loads the catalogs and narrows the locale resolver" {
            // The resolver is the half that makes this more than a Messages bean: WebFlux's default
            // answers with whatever Accept-Language asked for, catalog or no catalog.
            i18n()
                .withPropertyValues("stx.i18n.enabled=true", "stx.i18n.languages=en,fr", "stx.i18n.fallback=fr")
                .run { context ->
                    context.getBean(Messages::class.java).fallback shouldBe Locale.forLanguageTag("fr")
                    context.getBean(LocaleContextResolver::class.java) shouldBe
                        context.getBean("localeContextResolver")
                }
        }

        "i18n: the languages are a property, not a line in a @Configuration class" {
            // The version this replaces hardcoded listOf("en", "fr"), so adding a language meant
            // editing the framework rather than the deployment.
            i18n()
                .withPropertyValues("stx.i18n.enabled=true", "stx.i18n.languages=en,fr,de")
                .run { context ->
                    val resolver = context.getBean(AcceptHeaderLocaleContextResolver::class.java)
                    resolver.supportedLocales shouldBe listOf("en", "fr", "de").map(Locale::forLanguageTag)
                }
        }
    })

/** A Redis and a broker that are already up, or null. Nothing here starts a container. */
private val REDIS: String? = System.getenv("REDIS_TEST_URI")
private val AMQP: String? = System.getenv("AMQP_TEST_URI")

private fun mongo() = runnerFor(MongoIntegrationAutoConfiguration::class.java)

private fun kafka() = runnerFor(KafkaIntegrationAutoConfiguration::class.java)

private fun redis() = runnerFor(RedisIntegrationAutoConfiguration::class.java)

private fun amqp() = runnerFor(AmqpIntegrationAutoConfiguration::class.java)

private fun i18n() = runnerFor(I18nIntegrationAutoConfiguration::class.java)

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
