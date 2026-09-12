package com.softistx.telemetry.export

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The half of rotation that is a directory and some names, tested without a byte of telemetry.
 *
 * Splitting it out of [FileExporter] is what makes this possible, and what makes it worth doing: the
 * questions here — does an hourly file roll at the top of the hour or an hour after it opened, does
 * retention count the file being written — are answered with `writeText` and a fake clock, in
 * milliseconds, instead of through a pipeline.
 */
class RotationTest :
    FeatureSpec({
        val midnight = Instant.parse("2026-09-01T00:00:00Z")

        val directories = mutableListOf<Path>()
        afterSpec { directories.forEach { it.toFile().deleteRecursively() } }

        fun directory(): Path = createTempDirectory("rotation").also(directories::add)

        fun rotation(
            path: Path,
            maxSize: Long = 0,
            every: Duration? = null,
            keep: Int = 7,
            compress: Boolean = false,
        ) = Rotation(path, maxSize, every, keep, compress)

        feature("when a file is due") {
            scenario("a size limit is the size the file has reached, not the one it passed") {
                val subject = rotation(directory().resolve("t.jsonl"), maxSize = 100)

                subject.due(size = 99, openedAt = midnight, now = midnight) shouldBe false
                subject.due(size = 100, openedAt = midnight, now = midnight) shouldBe true
            }

            scenario("a period is counted from the epoch, so a day ends at midnight") {
                val subject = rotation(directory().resolve("t.jsonl"), every = 24.hours)
                val opened = Instant.parse("2026-09-01T23:30:00Z")

                // Ten hours later, still the same day: 24 hours after opening would have rolled.
                subject.due(size = 1, openedAt = midnight, now = Instant.parse("2026-09-01T10:00:00Z")) shouldBe false
                // Forty minutes later, and it is a different day.
                subject.due(size = 1, openedAt = opened, now = Instant.parse("2026-09-02T00:10:00Z")) shouldBe true
            }

            scenario("an empty file is never rolled, whatever the clock says") {
                // Otherwise a service that is quiet overnight rolls eight empty files and prunes the
                // eight real ones out of existence behind them.
                val subject = rotation(directory().resolve("t.jsonl"), every = 1.hours, keep = 3)

                subject.due(size = 0, openedAt = midnight, now = midnight + 9.hours) shouldBe false
            }

            scenario("neither limit means never") {
                val subject = rotation(directory().resolve("t.jsonl"))

                subject.due(size = Long.MAX_VALUE, openedAt = midnight, now = midnight + 9.hours) shouldBe false
            }
        }

        feature("rolling") {
            scenario("the active file is moved to a name stamped with the moment, in UTC") {
                val directory = directory()
                val path = directory.resolve("telemetry.jsonl")
                path.writeText("one\n")

                rotation(path).roll(Instant.parse("2026-09-01T14:02:11Z"))

                path.exists() shouldBe false
                directory.resolve("telemetry-20260901-140211.jsonl").exists() shouldBe true
            }

            scenario("a second roll in the same second does not overwrite the first") {
                val directory = directory()
                val path = directory.resolve("telemetry.jsonl")
                val subject = rotation(path)
                val at = Instant.parse("2026-09-01T14:02:11Z")

                path.writeText("first\n")
                subject.roll(at)
                path.writeText("second\n")
                subject.roll(at)

                subject.existing() shouldHaveSize 2
                directory.resolve("telemetry-20260901-140211-1.jsonl").exists() shouldBe true
            }

            scenario("rolling a file that is not there does nothing at all") {
                val path = directory().resolve("telemetry.jsonl")

                rotation(path).roll(midnight)

                rotation(path).existing() shouldHaveSize 0
            }
        }

        feature("retention") {
            scenario("only the newest keep survive, and the active file is not one of them") {
                val directory = directory()
                val path = directory.resolve("telemetry.jsonl")
                val subject = rotation(path, keep = 2)

                repeat(4) { hour ->
                    path.writeText("hour $hour\n")
                    subject.roll(midnight + hour.hours)
                }
                path.writeText("current\n")

                subject.existing().map { it.name }.sorted() shouldBe
                    listOf("telemetry-20260901-020000.jsonl", "telemetry-20260901-030000.jsonl")
                path.exists() shouldBe true
            }

            scenario("keep = 0 leaves only the file being written") {
                val directory = directory()
                val path = directory.resolve("telemetry.jsonl")
                val subject = rotation(path, keep = 0)

                path.writeText("one\n")
                subject.roll(midnight)

                subject.existing() shouldHaveSize 0
            }
        }

        feature("compression") {
            scenario("the rolled file is gzipped, and holds what the plain one held") {
                val directory = directory()
                val path = directory.resolve("telemetry.jsonl")
                path.writeText("one\ntwo\n")

                rotation(path, compress = true).roll(midnight)

                val rolled = directory.resolve("telemetry-20260901-000000.jsonl.gz")
                rolled.exists() shouldBe true
                directory.resolve("telemetry-20260901-000000.jsonl").exists() shouldBe false
                GZIPInputStream(rolled.inputStream()).use { it.readBytes().decodeToString() } shouldBe "one\ntwo\n"
            }

            scenario("a gzipped file still counts towards retention") {
                val directory = directory()
                val path = directory.resolve("telemetry.jsonl")
                val subject = rotation(path, keep = 1, compress = true)

                repeat(3) { hour ->
                    path.writeText("hour $hour\n")
                    subject.roll(midnight + hour.hours)
                }

                subject.existing() shouldHaveSize 1
                subject.existing().single().name shouldEndWith ".jsonl.gz"
            }
        }

        feature("what it refuses") {
            scenario("a period that rounds to no seconds at all") {
                val thrown =
                    runCatching { rotation(directory().resolve("t.jsonl"), every = Duration.ZERO) }
                        .exceptionOrNull()

                thrown.shouldNotBeNull().message.shouldNotBeNull() shouldContain "rounds to none"
            }
        }
    })
