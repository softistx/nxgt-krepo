package com.strange.telemetry.spring

import com.strange.telemetry.Telemetry
import com.strange.telemetry.logger
import com.strange.telemetry.slf4j.Slf4jExporter
import com.strange.telemetry.span
import com.strange.telemetry.spring.fixture.Recorder
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.slf4j.event.Level
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * `stx.telemetry.slf4j` — the bridge pointed outward, wired by a property instead of a `@Bean`.
 *
 * The counterpart of `stx.telemetry.otlp`, and it was missing for a release: the exporter existed and
 * the auto-configuration had no key for it, so the only way to reach it was to declare the bean the
 * key exists to save you writing.
 */
class TelemetrySlf4jConfigurationTest :
    FeatureSpec({
        val log = logger("orders")
        val runner =
            ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TelemetryAutoConfiguration::class.java))
                .withPropertyValues("stx.telemetry.enabled=true")

        feature("opt-in") {
            scenario("no exporter unless the key says so") {
                runner.withUserConfiguration(WithRecorder::class.java).run { context ->
                    context.getBeanNamesForType(Slf4jExporter::class.java).size shouldBe 0
                }
            }

            scenario("the key builds one, without a @Bean method") {
                runner
                    .withPropertyValues("stx.telemetry.slf4j.enabled=true")
                    .withUserConfiguration(WithRecorder::class.java)
                    .run { context -> context.getBean(Slf4jExporter::class.java).shouldNotBeNull() }
            }
        }

        feature("what it writes") {
            scenario("a log written in a span reaches SLF4J with the ids in the MDC") {
                runner
                    .withPropertyValues("stx.telemetry.slf4j.enabled=true")
                    .withUserConfiguration(WithRecorder::class.java)
                    .run { context ->
                        val recorder = context.getBean(Recorder::class.java)

                        val traceId =
                            runBlocking {
                                span("charge") {
                                    log.info("charged", "orderId" to "o-1")
                                    this.context.traceId.hex
                                }
                            }
                        // Closing drains, so everything queued has been through the exporter by now.
                        context.getBean(Telemetry::class.java).close()

                        val line = recorder.line("charged").shouldNotBeNull()
                        line.logger shouldBe "orders"
                        line.level shouldBe Level.INFO
                        // %X{traceId} in a logback pattern is the whole point of putting them there.
                        line.traceId shouldBe traceId
                        line.attribute("orderId") shouldBe "o-1"
                    }
            }

            scenario("the span is logged too, at the level the key asks for") {
                runner
                    .withPropertyValues("stx.telemetry.slf4j.enabled=true", "stx.telemetry.slf4j.span-severity=debug")
                    .withUserConfiguration(WithRecorder::class.java)
                    .run { context ->
                        val recorder = context.getBean(Recorder::class.java)

                        runBlocking { span("charge") { } }
                        context.getBean(Telemetry::class.java).close()

                        val span = recorder.lines.firstOrNull { it.logger == "com.strange.telemetry.span" }
                        span.shouldNotBeNull().level shouldBe Level.DEBUG
                        span.message shouldContain "charge"
                    }
            }

            scenario("spans=false leaves the logs and drops the spans") {
                runner
                    .withPropertyValues("stx.telemetry.slf4j.enabled=true", "stx.telemetry.slf4j.spans=false")
                    .withUserConfiguration(WithRecorder::class.java)
                    .run { context ->
                        val recorder = context.getBean(Recorder::class.java)

                        runBlocking { span("charge") { log.info("charged") } }
                        context.getBean(Telemetry::class.java).close()

                        recorder.line("charged").shouldNotBeNull()
                        recorder.lines.count { it.logger == "com.strange.telemetry.span" } shouldBe 0
                    }
            }
        }

        feature("both directions at once") {
            scenario("with the provider bound and no factory bean, the context refuses to start") {
                // This module's test classpath binds SLF4J to stx-telemetry's own provider, which is
                // the loop: SLF4J into the pipeline, the pipeline back into SLF4J. It has to fail
                // here, loudly, rather than at the hour the two of them fill a queue with each other.
                runner.withPropertyValues("stx.telemetry.slf4j.enabled=true").run { context ->
                    context.startupFailure
                        .shouldNotBeNull()
                        .stackTraceToString() shouldContain "feed this pipeline"
                }
            }
        }
    })

@Configuration(proxyBeanMethods = false)
private class WithRecorder {
    @Bean
    fun recorder(): Recorder = Recorder()
}
