package com.strange.spring.data.mongo.migration

import com.mongodb.reactivestreams.client.MongoClients
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory

@Configuration(proxyBeanMethods = false)
private class TemplateOnly {
    @Bean
    fun template(): ReactiveMongoTemplate =
        ReactiveMongoTemplate(
            SimpleReactiveMongoDatabaseFactory(MongoClients.create("mongodb://localhost:27017"), "stx_wiring"),
        )
}

@Configuration(proxyBeanMethods = false)
private class OwnStore {
    @Bean
    fun migrationStore(): MigrationStore =
        MigrationStore(
            ReactiveMongoTemplate(
                SimpleReactiveMongoDatabaseFactory(MongoClients.create("mongodb://localhost:27017"), "stx_wiring"),
            ),
            "somewhere_else",
        )
}

class MigrationAutoConfigurationTest :
    StringSpec({
        val runner =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MigrationAutoConfiguration::class.java))
                .withUserConfiguration(TemplateOnly::class.java)

        "nothing runs until an application asks for it" {
            // The version this was extracted from defaulted to enabled, so adding the library to a
            // classpath was enough to have it write to the database at the next startup.
            runner.run { context ->
                context.getBeanNamesForType(MigrationRunner::class.java).size shouldBe 0
                context.getBeanNamesForType(MigrationStore::class.java).size shouldBe 0
            }
        }

        "enabling it registers the runner and its store" {
            runner.withPropertyValues("stx.data.mongo.migration.enabled=true").run { context ->
                context.getBeanNamesForType(MigrationStore::class.java).size shouldBe 1
                context.getBeanNamesForType(MigrationRunner::class.java).size shouldBe 1
            }
        }

        "an application's own store wins" {
            runner
                .withPropertyValues("stx.data.mongo.migration.enabled=true")
                .withUserConfiguration(OwnStore::class.java)
                .run { context ->
                    context.getBeanNamesForType(MigrationStore::class.java).toList() shouldBe listOf("migrationStore")
                }
        }

        "the prefix and collection are configurable" {
            runner
                .withPropertyValues(
                    "stx.data.mongo.migration.enabled=true",
                    "stx.data.mongo.migration.prefix=M",
                    "stx.data.mongo.migration.collection=schema_history",
                ).run { context ->
                    val properties = context.getBean(MigrationProperties::class.java)
                    properties.prefix shouldBe "M"
                    properties.collection shouldBe "schema_history"
                }
        }

        "a unit is not even constructed while migration is off" {
            // `@MigrationUnit` is `@ConditionalOnProperty`, so the units — which typically inject a
            // template each — are not built for a feature nobody turned on.
            val withUnit = runner.withUserConfiguration(ScannedUnit::class.java)

            withUnit.run { context ->
                context.getBeanNamesForType(Migration::class.java).size shouldBe 0
            }

            // The other half, or the assertion above would pass on a unit that was never registered
            // at all and prove nothing about the condition.
            withUnit.withPropertyValues("stx.data.mongo.migration.enabled=true").run { context ->
                context.getBeanNamesForType(Migration::class.java).size shouldBe 1
            }
        }
    })

/** A component-scanned unit, declared the way an application declares one. */
@MigrationUnit("does nothing")
private class V9Nothing : Migration {
    override suspend fun migrate() = Unit
}

@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Import(V9Nothing::class)
private class ScannedUnit
