package com.strange.spring.data.mongo.audit

import com.mongodb.reactivestreams.client.MongoClients
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.isActive
import org.javers.core.Javers
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

class AuditAutoConfigurationTest :
    StringSpec({
        val runner =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AuditAutoConfiguration::class.java))
                .withUserConfiguration(TemplateOnly::class.java)

        "nothing is recorded until an application asks for it" {
            // An audit trail is a second copy of the data. Putting the library on a classpath must
            // not start writing one.
            runner.run { context ->
                context.getBeanNamesForType(AuditListener::class.java).size shouldBe 0
                context.getBeanNamesForType(Javers::class.java).size shouldBe 0
            }
        }

        "enabling it registers the whole chain" {
            runner.withPropertyValues("stx.data.mongo.audit.enabled=true").run { context ->
                context.getBeanNamesForType(Javers::class.java).size shouldBe 1
                context.getBeanNamesForType(AuditStore::class.java).size shouldBe 1
                context.getBeanNamesForType(AuditTrail::class.java).size shouldBe 1
                context.getBeanNamesForType(AuditListener::class.java).size shouldBe 1
            }
        }

        "the scope it writes on is cancelled when the context closes" {
            // The reason the scope is a bean at all, rather than GlobalScope: writers that outlive
            // the context are stopped only by the process exiting, which loses whatever is in flight
            // and leaves nothing for a test to wait on.
            var scope: AuditScope? = null

            runner.withPropertyValues("stx.data.mongo.audit.enabled=true").run { context ->
                scope = context.getBean(AuditScope::class.java)
                scope!!.scope.isActive shouldBe true
            }

            // `run` closes the context on the way out.
            scope!!.scope.isActive shouldBe false
        }

        "an application's own Javers wins" {
            runner
                .withPropertyValues("stx.data.mongo.audit.enabled=true")
                .withBean(Javers::class.java, {
                    org.javers.core.JaversBuilder
                        .javers()
                        .build()
                })
                .run { context -> context.getBeanNamesForType(Javers::class.java).size shouldBe 1 }
        }

        "the collection name is configurable" {
            runner
                .withPropertyValues("stx.data.mongo.audit.enabled=true", "stx.data.mongo.audit.collection=history")
                .run { context -> context.getBean(AuditProperties::class.java).collection shouldBe "history" }
        }
    })
