package com.strange.telemetry.export

import com.strange.telemetry.model.LogRecord
import com.strange.telemetry.model.Resource
import com.strange.telemetry.model.Severity
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The file, its lines, and what a restart does to it.
 *
 * The rotation *policy* is [RotationTest]'s; what is here is the part that owns an open stream —
 * that the lines are the ones a collector already knows how to read, that a roll loses nothing
 * between closing one file and opening the next, and that a process coming back up continues the
 * file it left rather than starting a fresh one per deploy.
 */
class FileExporterTest :
    FeatureSpec({
        val start = Instant.parse("2026-09-01T10:00:00Z")
        val resource = Resource("checkout")

        val directories = mutableListOf<Path>()
        afterSpec { directories.forEach { it.toFile().deleteRecursively() } }

        fun directory(): Path = createTempDirectory("file-exporter").also(directories::add)

        fun log(name: String) = LogRecord(at = start, severity = Severity.Info, name = name, source = "orders")

        class Ticking(
            var at: Instant,
        ) : Clock {
            override fun now(): Instant = at
        }

        feature("the lines") {
            scenario("one JSON object per line, exactly what JsonLinesExporter writes to stdout") {
                val path = directory().resolve("telemetry.jsonl")
                val stdout = ByteArrayOutputStream()

                FileExporter(path, clock = Ticking(start)).use { it.export(resource, listOf(log("charged"))) }
                JsonLinesExporter(PrintStream(stdout)).export(resource, listOf(log("charged")))

                // Byte for byte: the same parser reads both, and moving from one to the other
                // changes nothing downstream.
                path.readText() shouldBe stdout.toString()
            }

            scenario("the fields are the signal's own, discriminated by type") {
                val path = directory().resolve("telemetry.jsonl")

                FileExporter(path, clock = Ticking(start)).use { it.export(resource, listOf(log("charged"))) }

                val line = Json.parseToJsonElement(path.readText().trim()).jsonObject
                line["type"]!!.jsonPrimitive.content shouldBe "log"
                line["name"]!!.jsonPrimitive.content shouldBe "charged"
            }

            scenario("a second batch appends rather than replacing") {
                val path = directory().resolve("telemetry.jsonl")

                FileExporter(path, clock = Ticking(start)).use {
                    it.export(resource, listOf(log("one")))
                    it.export(resource, listOf(log("two")))
                }

                path.readText().trim().lines() shouldHaveSize 2
            }

            scenario("the parent directory is created, so a fresh deployment needs no mkdir") {
                val path = directory().resolve("nested/deeper/telemetry.jsonl")

                FileExporter(path, clock = Ticking(start)).use { it.export(resource, listOf(log("charged"))) }

                path.exists() shouldBe true
            }
        }

        feature("rolling on size") {
            scenario("what came before is in the rolled file and not in the new one") {
                val directory = directory()
                val path = directory.resolve("telemetry.jsonl")

                FileExporter(path, maxSize = 1, clock = Ticking(start)).use {
                    it.export(resource, listOf(log("first")))
                    it.export(resource, listOf(log("second")))
                }

                // The first line filled the file, so the second opened a new one.
                path.readText() shouldContain "second"
                path.readText().trim().lines() shouldHaveSize 1
                val rolled = directory.toFile().list()!!.single { it != "telemetry.jsonl" }
                directory.resolve(rolled).readText() shouldContain "first"
            }
        }

        feature("rolling on a period") {
            scenario("the boundary rolls the file, and the clock moving inside one does not") {
                val directory = directory()
                val path = directory.resolve("telemetry.jsonl")
                val clock = Ticking(start)

                FileExporter(path, maxSize = 0, every = 24.hours, keep = 7, clock = clock).use {
                    it.export(resource, listOf(log("morning")))
                    clock.at = start + 6.hours
                    it.export(resource, listOf(log("afternoon")))
                    clock.at = start + 20.hours
                    it.export(resource, listOf(log("tomorrow")))
                }

                // Same UTC day for the first two, the next one for the third.
                path.readText().trim().lines() shouldHaveSize 1
                path.readText() shouldContain "tomorrow"
                val rolled = directory.toFile().list()!!.single { it != "telemetry.jsonl" }
                directory
                    .resolve(rolled)
                    .readText()
                    .trim()
                    .lines() shouldHaveSize 2
            }
        }

        feature("a restart") {
            scenario("it continues the file it left, rather than one per deploy") {
                val path = directory().resolve("telemetry.jsonl")

                FileExporter(path, clock = Ticking(start)).use { it.export(resource, listOf(log("before"))) }
                FileExporter(path, clock = Ticking(start)).use { it.export(resource, listOf(log("after"))) }

                path.readText().trim().lines() shouldHaveSize 2
            }

            scenario("closing rolls nothing, so a service that restarts often keeps its window") {
                val directory = directory()
                val path = directory.resolve("telemetry.jsonl")

                repeat(3) { FileExporter(path, clock = Ticking(start)).use { e -> e.export(resource, listOf(log("$it"))) } }

                directory.toFile().list()!!.single() shouldBe "telemetry.jsonl"
            }
        }

        feature("closing") {
            scenario("twice is harmless — a DI container closes what it hands out") {
                val exporter = FileExporter(directory().resolve("telemetry.jsonl"), clock = Ticking(start))

                exporter.close()
                exporter.close()
            }

            scenario("a batch that arrives after close is dropped, not thrown at the pipeline") {
                val path = directory().resolve("telemetry.jsonl")
                val exporter = FileExporter(path, clock = Ticking(start))

                exporter.close()
                exporter.export(resource, listOf(log("late")))

                path.readText() shouldBe ""
            }
        }

        feature("what it refuses at construction") {
            scenario("a path whose parent cannot be a directory, while somebody is still watching") {
                val directory = directory()
                val blocker = directory.resolve("occupied")
                blocker.toFile().writeText("not a directory")

                val thrown =
                    runCatching { FileExporter(blocker.resolve("telemetry.jsonl"), clock = Ticking(start)) }
                        .exceptionOrNull()

                thrown.shouldNotBeNull()
            }
        }
    })
