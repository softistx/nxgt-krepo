package com.strange.telemetry.spring

import com.strange.telemetry.Telemetry
import com.strange.telemetry.export.Exporter
import com.strange.telemetry.model.Severity
import com.strange.telemetry.otlp.OtlpExporter
import com.strange.telemetry.spring.fixture.Collector
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class TelemetryAutoConfigurationTest :
    FeatureSpec({
        val runner =
            ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TelemetryAutoConfiguration::class.java))

        feature("opt-in") {
            scenario("nothing is built without stx.telemetry.enabled") {
                runner.run { context ->
                    context.getBeanNamesForType(Telemetry::class.java).size shouldBe 0
                    context.getBeanNamesForType(TelemetryWebFilter::class.java).size shouldBe 0
                    // Putting the library on a classpath must not change where logs go.
                    Telemetry.installed shouldBe null
                }
            }

            scenario("enabled builds a telemetry and installs it, so logger<T>() finds it") {
                runner.withPropertyValues("stx.telemetry.enabled=true", "spring.application.name=checkout").run { context ->
                    val telemetry = context.getBean(Telemetry::class.java)
                    telemetry.resource.service shouldBe "checkout"
                    Telemetry.installed shouldBe telemetry
                    context.getBean(TelemetryWebFilter::class.java).shouldNotBeNull()
                }
            }

            scenario("closing the context stands the telemetry down") {
                runner.withPropertyValues("stx.telemetry.enabled=true").run { }
                Telemetry.installed shouldBe null
            }
        }

        feature("the resource") {
            scenario("stx.telemetry.service wins over spring.application.name") {
                runner
                    .withPropertyValues(
                        "stx.telemetry.enabled=true",
                        "spring.application.name=boot-name",
                        "stx.telemetry.service=checkout",
                        "stx.telemetry.version=1.4.0",
                        "stx.telemetry.environment=production",
                    ).run { context ->
                        val resource = context.getBean(Telemetry::class.java).resource
                        resource.service shouldBe "checkout"
                        resource.version shouldBe "1.4.0"
                        resource.environment shouldBe "production"
                    }
            }

            scenario("with neither, the name says so rather than being empty") {
                runner.withPropertyValues("stx.telemetry.enabled=true").run { context ->
                    context.getBean(Telemetry::class.java).resource.service shouldBe "unknown-service"
                }
            }

            scenario("the severity floor is a property") {
                runner
                    .withPropertyValues("stx.telemetry.enabled=true", "stx.telemetry.minimum=warn")
                    .run { context -> context.getBean(Telemetry::class.java).minimum shouldBe Severity.Warn }
            }
        }

        feature("exporters") {
            scenario("every Exporter bean is added to the root") {
                runner
                    .withPropertyValues("stx.telemetry.enabled=true")
                    .withUserConfiguration(WithCollector::class.java)
                    .run { context ->
                        val collector = context.getBean(Exporter::class.java) as Collector
                        context.getBean(Telemetry::class.java).close()
                        // Closing drains, so anything the root queued reached this exporter.
                        collector.closed shouldBe true
                    }
            }

            scenario("stx.telemetry.otlp builds one without a @Bean method") {
                runner
                    .withPropertyValues(
                        "stx.telemetry.enabled=true",
                        "stx.telemetry.otlp.enabled=true",
                        "stx.telemetry.otlp.endpoint=http://collector:4318",
                    ).run { context -> context.getBean(OtlpExporter::class.java).shouldNotBeNull() }
            }

            scenario("it is off unless asked for") {
                runner.withPropertyValues("stx.telemetry.enabled=true").run { context ->
                    context.getBeanNamesForType(OtlpExporter::class.java).size shouldBe 0
                }
            }
        }

        feature("what an application declares itself") {
            scenario("its own Telemetry bean wins") {
                runner
                    .withPropertyValues("stx.telemetry.enabled=true")
                    .withUserConfiguration(WithTelemetry::class.java)
                    .run { context ->
                        context.getBean(Telemetry::class.java).resource.service shouldBe "mine"
                    }
            }
        }
    })

@Configuration(proxyBeanMethods = false)
private class WithCollector {
    @Bean
    fun collector(): Exporter = Collector()
}

@Configuration(proxyBeanMethods = false)
private class WithTelemetry {
    @Bean(destroyMethod = "close")
    fun telemetry(): Telemetry = Telemetry("mine")
}
