package com.strange.spring.data.mongo.config

import com.mongodb.reactivestreams.client.MongoClients
import com.strange.spring.testing.UNREACHABLE_MONGO
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.ReactiveAuditorAware
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.mongodb.gridfs.ReactiveGridFsTemplate
import java.math.BigDecimal
import kotlin.time.Instant

/**
 * What Spring Boot would otherwise have provided, stood in for.
 *
 * The wiring is what these specs are about; `FindPageTest` is where a real server is paged through.
 * So the client is pointed at [UNREACHABLE_MONGO] rather than at a plausible-looking `localhost:27017`
 * — creating a client opens no connection *here*, but the driver's cluster monitor connects on its
 * own thread, and on a developer machine 27017 answers. These specs asserted bean wiring while
 * holding an open connection to somebody's real database.
 */
@Configuration(proxyBeanMethods = false)
private class MongoInfrastructure {
    @Bean
    fun factory(): ReactiveMongoDatabaseFactory = SimpleReactiveMongoDatabaseFactory(MongoClients.create(UNREACHABLE_MONGO), "stx_wiring")

    /**
     * An `ObjectProvider`, because half these specs run with the auto-configuration switched off and
     * there is then no conversions bean to inject — which is exactly what they are asserting.
     */
    @Bean
    fun converter(conversions: ObjectProvider<MongoCustomConversions>): MappingMongoConverter =
        MappingMongoConverter(NoOpDbRefResolver.INSTANCE, MongoMappingContext().apply { afterPropertiesSet() })
            .apply {
                setCustomConversions(conversions.getIfAvailable { MongoCustomConversions(emptyList<Any>()) })
                afterPropertiesSet()
            }

    @Bean
    fun template(
        factory: ReactiveMongoDatabaseFactory,
        converter: MappingMongoConverter,
    ): ReactiveMongoTemplate = ReactiveMongoTemplate(factory, converter)
}

class MongoAutoConfigurationTest :
    StringSpec({
        val runner =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MongoAutoConfiguration::class.java))
                .withUserConfiguration(MongoInfrastructure::class.java)

        "nothing is registered until an application asks for it" {
            runner.run { context ->
                context.getBeanNamesForType(MongoCustomConversions::class.java).size shouldBe 0
                context.getBeanNamesForType(ReactiveGridFsTemplate::class.java).size shouldBe 0
                context.getBeanNamesForType(ReactiveMongoTransactionManager::class.java).size shouldBe 0
                context.getBeanNamesForType(IndexInitializer::class.java).size shouldBe 0
            }
        }

        "enabling it registers the Instant converters" {
            // Without them an entity holding a kotlin.time.Instant fails at *query* time with
            // "Can't find a codec", not at mapping time — a field that looks fine until read.
            runner.withPropertyValues("stx.data.mongo.enabled=true").run { context ->
                val conversions = context.getBean(MongoCustomConversions::class.java)

                conversions.hasCustomWriteTarget(Instant::class.java) shouldBe true
            }
        }

        "and Spring Boot's own representation setting still applies" {
            // The reason this configuration is ordered *before* Boot's: Boot's conversions bean is
            // @ConditionalOnMissingBean, so contributing one without the ordering replaces it and
            // silently drops this property.
            runner
                .withPropertyValues(
                    "stx.data.mongo.enabled=true",
                    "spring.data.mongodb.representation.big-decimal=STRING",
                ).run { context ->
                    val conversions = context.getBean(MongoCustomConversions::class.java)

                    conversions.getCustomWriteTarget(BigDecimal::class.java).get() shouldBe String::class.java
                    // and the stx converters are still there beside it
                    conversions.hasCustomWriteTarget(Instant::class.java) shouldBe true
                }
        }

        "a GridFS template appears only when a bucket is named" {
            runner.withPropertyValues("stx.data.mongo.enabled=true").run { context ->
                context.getBeanNamesForType(ReactiveGridFsTemplate::class.java).size shouldBe 0
            }
            runner
                .withPropertyValues("stx.data.mongo.enabled=true", "stx.data.mongo.gridfs-bucket=uploads")
                .run { context -> context.getBeanNamesForType(ReactiveGridFsTemplate::class.java).size shouldBe 1 }
        }

        "a transaction manager appears only when asked for" {
            runner
                .withPropertyValues("stx.data.mongo.enabled=true", "stx.data.mongo.transactions=true")
                .run { context -> context.getBeanNamesForType(ReactiveMongoTransactionManager::class.java).size shouldBe 1 }
        }

        "index creation appears only when asked for" {
            runner
                .withPropertyValues("stx.data.mongo.enabled=true", "stx.data.mongo.create-indexes=true")
                .run { context -> context.getBeanNamesForType(IndexInitializer::class.java).size shouldBe 1 }
        }

        "the auditor appears only when asked for" {
            runner.withPropertyValues("stx.data.mongo.enabled=true").run { context ->
                context.getBeanNamesForType(ReactiveAuditorAware::class.java).size shouldBe 0
            }
            runner
                .withPropertyValues("stx.data.mongo.enabled=true", "stx.data.mongo.auditor=true")
                .run { context -> context.getBeanNamesForType(ReactiveAuditorAware::class.java).size shouldBe 1 }
        }

        "an application's own conversions win" {
            runner
                .withPropertyValues("stx.data.mongo.enabled=true")
                .withBean(MongoCustomConversions::class.java, { MongoCustomConversions(emptyList<Any>()) })
                .run { context ->
                    context.getBeanNamesForType(MongoCustomConversions::class.java).size shouldBe 1
                    context.getBean(MongoCustomConversions::class.java).hasCustomWriteTarget(Instant::class.java) shouldBe false
                }
        }
    })
