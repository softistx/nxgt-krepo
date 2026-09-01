package com.softistx.telemetry.spring

import com.softistx.telemetry.Telemetry
import com.softistx.telemetry.export.FileExporter
import com.softistx.telemetry.logger
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText

/** `stx.telemetry.file` — the rotating file, wired by a property. */
class TelemetryFileConfigurationTest :
    FeatureSpec({
        val log = logger("orders")
        val directories = mutableListOf<Path>()
        afterSpec { directories.forEach { it.toFile().deleteRecursively() } }

        fun directory(): Path = createTempDirectory("file-config").also(directories::add)

        val runner =
            ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TelemetryAutoConfiguration::class.java))
                .withPropertyValues("stx.telemetry.enabled=true")

        feature("opt-in") {
            scenario("no file unless the key says so") {
                runner.run { context -> context.getBeanNamesForType(FileExporter::class.java).size shouldBe 0 }
            }

            scenario("the key builds one and the lines land in it") {
                val path = directory().resolve("telemetry.jsonl")

                runner
                    .withPropertyValues("stx.telemetry.file.enabled=true", "stx.telemetry.file.path=$path")
                    .run { context ->
                        context.getBean(FileExporter::class.java).shouldNotBeNull()

                        runBlocking { log.info("charged", "orderId" to "o-1") }
                        // Closing drains, so the batch has been through the exporter and flushed.
                        context.getBean(Telemetry::class.java).close()

                        path.readText() shouldContain "\"name\":\"charged\""
                    }
            }
        }

        feature("the settings") {
            scenario("a data size and a duration bind, and zero turns a limit off") {
                val path = directory().resolve("telemetry.jsonl")

                runner
                    .withPropertyValues(
                        "stx.telemetry.file.enabled=true",
                        "stx.telemetry.file.path=$path",
                        "stx.telemetry.file.max-size=512KB",
                        "stx.telemetry.file.every=0",
                        "stx.telemetry.file.keep=2",
                    ).run { context ->
                        val properties = context.getBean(TelemetryFileProperties::class.java)
                        properties.maxSize.toBytes() shouldBe 512 * 1024
                        properties.every.isZero shouldBe true
                        properties.keep shouldBe 2
                        // `every: 0` has to reach the exporter as "no time limit" rather than as a
                        // period of no seconds, which the exporter refuses outright.
                        context.getBean(FileExporter::class.java).shouldNotBeNull()
                    }
            }
        }
    })
