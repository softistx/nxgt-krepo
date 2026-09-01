package com.strange.telemetry.export

import com.strange.telemetry.Telemetry
import com.strange.telemetry.context.withTelemetry
import com.strange.telemetry.fixture.Collector
import com.strange.telemetry.logger
import com.strange.telemetry.model.LogRecord
import com.strange.telemetry.model.Resource
import com.strange.telemetry.model.Severity
import com.strange.telemetry.model.Signal
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class PipelineTest :
    FeatureSpec({
        val log = logger("pipeline-spec")

        feature("writing a log never makes a caller wait") {
            scenario("ten thousand logs are queued while the exporter is asleep") {
                val collector = Collector(slow = 200.milliseconds)
                val telemetry =
                    Telemetry("spec") {
                        minimum = Severity.Debug
                        batch = 1
                        linger = 1.milliseconds
                        // The point is the producer, not the drain: do not sit here for the backlog.
                        drainTimeout = 50.milliseconds
                        export(collector)
                    }

                val elapsed =
                    TimeSource.Monotonic.markNow().let { start ->
                        withTelemetry(telemetry) { repeat(10_000) { log.info("n", "i" to it) } }
                        start.elapsedNow()
                    }
                telemetry.close()

                // The exporter alone would need half an hour at 200ms a batch; the producer never met it.
                (elapsed < 2.seconds) shouldBe true
            }
        }

        feature("closing") {
            scenario("it ships the backlog before it returns") {
                val collector = Collector()
                val telemetry =
                    Telemetry("spec") {
                        minimum = Severity.Debug
                        // Big enough that nothing ships on size, slow enough that nothing ships on time.
                        batch = 100_000
                        linger = 1.seconds
                        export(collector)
                    }

                withTelemetry(telemetry) { repeat(500) { log.info("n", "i" to it) } }
                collector.logs.size shouldBe 0
                telemetry.close()

                collector.logs.size shouldBe 500
                collector.closed shouldBe true
            }

            scenario("it is safe twice, and stands down as the installed default") {
                val telemetry = Telemetry("spec") { export(Collector()) }.install()
                Telemetry.installed shouldBe telemetry
                telemetry.close()
                telemetry.close()
                Telemetry.installed shouldBe null
            }

            scenario("a log written after it is dropped rather than throwing") {
                val collector = Collector()
                val telemetry = Telemetry("spec") { export(collector) }
                telemetry.close()
                withTelemetry(telemetry) { log.error("too late") }
                collector.logs.size shouldBe 0
            }
        }

        feature("batching") {
            scenario("a full batch ships without waiting for the linger") {
                val collector = Collector()
                val telemetry =
                    Telemetry("spec") {
                        minimum = Severity.Debug
                        batch = 10
                        linger = 1.hours
                        export(collector)
                    }
                withTelemetry(telemetry) { repeat(10) { log.info("n") } }

                var waited = 0
                while (collector.logs.size < 10 && waited < 200) {
                    delay(10)
                    waited += 10
                }
                collector.logs.size shouldBe 10
                telemetry.close()
            }

            scenario("a partial batch ships once the linger is up") {
                val collector = Collector()
                val telemetry =
                    Telemetry("spec") {
                        minimum = Severity.Debug
                        batch = 100_000
                        linger = 20.milliseconds
                        export(collector)
                    }
                withTelemetry(telemetry) { log.info("alone") }

                var waited = 0
                while (collector.logs.isEmpty() && waited < 1_000) {
                    delay(10)
                    waited += 10
                }
                collector.logs.size shouldBe 1
                telemetry.close()
            }
        }

        feature("an exporter that throws") {
            scenario("it is reported, and the ones after it still ship") {
                val collector = Collector()
                val failures = mutableListOf<Throwable>()
                val telemetry =
                    Telemetry("spec") {
                        minimum = Severity.Debug
                        linger = 1.milliseconds
                        onExportError = { failures += it }
                        export(Broken)
                        export(collector)
                    }

                withTelemetry(telemetry) { log.info("kept") }
                telemetry.close()

                failures.size shouldBe 1
                collector.logs.map { it.name } shouldBe listOf("kept")
            }
        }

        feature("json lines") {
            scenario("one object per signal, discriminated by type") {
                val bytes = ByteArrayOutputStream()
                JsonLinesExporter(PrintStream(bytes, true)).export(
                    Resource("spec"),
                    listOf(
                        LogRecord(
                            at = Clock.System.now(),
                            severity = Severity.Warn,
                            name = "slow",
                            source = "spec",
                        ) as Signal,
                    ),
                )

                val lines = bytes.toString().trim().lines()
                lines.size shouldBe 1
                val document = Json.parseToJsonElement(lines.single()).jsonObject
                document["type"]!!.jsonPrimitive.content shouldBe "log"
                document["name"]!!.jsonPrimitive.content shouldBe "slow"
                document["severity"]!!.jsonPrimitive.content shouldBe "Warn"
            }
        }
    })

private object Broken : Exporter {
    override suspend fun export(
        resource: Resource,
        batch: List<Signal>,
    ) = throw IllegalStateException("collector down")
}
