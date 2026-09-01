package com.strange.telemetry.spring

import com.strange.telemetry.mongo.MongoExporter
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import java.time.Duration

/**
 * `stx.telemetry.mongo` — the exporter, and the client it opens for itself.
 *
 * No server here on purpose: `MongoClient.create` connects on the first operation, and the exporter
 * touches the collection on the first batch, which this spec never sends. What the wiring has to get
 * right is which bean exists and what the properties bound to — and that is all reachable without one.
 */
class TelemetryMongoConfigurationTest :
    FeatureSpec({
        val runner =
            ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TelemetryAutoConfiguration::class.java))
                .withPropertyValues("stx.telemetry.enabled=true")

        feature("opt-in") {
            scenario("no exporter unless the key says so") {
                runner.run { context -> context.getBeanNamesForType(MongoExporter::class.java).size shouldBe 0 }
            }

            scenario("the key builds one, on a client of its own") {
                runner
                    .withPropertyValues("stx.telemetry.mongo.enabled=true", "stx.telemetry.mongo.uri=mongodb://db:27017")
                    .run { context ->
                        context.getBean(MongoExporter::class.java).shouldNotBeNull()
                        // No MongoClient bean is published: the exporter owns the one it opened, and
                        // an application with its own client keeps it to itself.
                        context.getBeanNamesForType(com.mongodb.kotlin.client.coroutine.MongoClient::class.java).size shouldBe 0
                    }
            }
        }

        feature("the settings") {
            scenario("the names and the retention bind, and zero keeps everything") {
                runner
                    .withPropertyValues(
                        "stx.telemetry.mongo.enabled=true",
                        "stx.telemetry.mongo.database=signals",
                        "stx.telemetry.mongo.collection=events",
                        "stx.telemetry.mongo.retention=0",
                    ).run { context ->
                        val properties = context.getBean(TelemetryMongoProperties::class.java)
                        properties.database shouldBe "signals"
                        properties.collection shouldBe "events"
                        properties.retention shouldBe Duration.ZERO
                        // Zero has to reach the exporter as "no index", not as a TTL of no seconds.
                        context.getBean(MongoExporter::class.java).shouldNotBeNull()
                    }
            }
        }
    })
