package com.strange.telemetry.export

import com.strange.common.concurrent.Guarded
import com.strange.common.lifecycle.CloseGuard
import com.strange.telemetry.model.Resource
import com.strange.telemetry.model.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * [JsonLinesExporter]'s format, to a file that is rotated.
 *
 * ```kotlin
 * Telemetry("checkout") {
 *     export(FileExporter(Path.of("logs/telemetry.jsonl"), maxSize = 64 * 1024 * 1024, every = 24.hours))
 * }
 * ```
 *
 * For a deployment that has no collector — a single VPS, an appliance, a job that has to leave
 * evidence behind — and for a container whose logs are already crowded with somebody else's output.
 * The lines are byte-for-byte the ones [JsonLinesExporter] writes to stdout, so the same parser reads
 * both and moving from one to the other changes nothing downstream.
 *
 * ## The two reasons to roll, and they are not alternatives
 *
 * [maxSize] bounds the disk; [every] bounds *how old the newest closed file is*. A service that logs
 * a little will fill 64 MB in a month, so a size limit alone means yesterday's telemetry is still in
 * the open file and nothing has been shipped anywhere. A service that logs a lot will blow through
 * the disk before midnight, so a period alone means the operator finds out at 3 a.m. Set both, which
 * is why both have defaults; either can be turned off — `maxSize = 0`, `every = null`.
 *
 * ## What it costs the pipeline
 *
 * A batch is written and flushed on the pipeline's single consumer, so a `tail -f` shows the last
 * batch and a crash loses at most that. A roll happens on the same thread — with [compress] on, so
 * does gzipping the file that was just closed, which for a 64 MB file is about a second of the
 * consumer not draining. The queue is unbounded and absorbs it; it is still the reason [compress]
 * is off by default rather than on.
 *
 * ## The file is opened when this is constructed
 *
 * A bad path, a directory that cannot be created, a file that cannot be written — all of it fails
 * here, where a Spring context or a `Telemetry { }` block is still being built and somebody is
 * watching. Deferring it to the first log means the failure arrives on the export path, where the
 * only thing that can be done with it is `onExportError`, and telemetry that was silently never
 * written is the failure this whole library exists to make visible.
 */
class FileExporter(
    private val path: Path,
    /** Bytes, after which the file is rolled. 0 for no size limit. */
    maxSize: Long = 64L * 1024 * 1024,
    /**
     * The period one file covers, aligned to the epoch — `24.hours` rolls at UTC midnight, not 24
     * hours after this process started. Null for no time limit.
     */
    every: Duration? = 24.hours,
    /** How many rolled files survive. 0 keeps only the file being written. */
    keep: Int = 7,
    /** Whether a rolled file is gzipped. Off by default: it happens on the export path. */
    compress: Boolean = false,
    private val clock: Clock = Clock.System,
) : Exporter {
    private val rotation = Rotation(path, maxSize, every, keep, compress)
    private val guard = CloseGuard()
    private val sink = Guarded(open())

    override suspend fun export(
        resource: Resource,
        batch: List<Signal>,
    ) {
        val lines = batch.map { signalLines.encodeToString(Signal.serializer(), it).toByteArray() }
        withContext(Dispatchers.IO) {
            sink.withLock { it.write(lines) }
        }
    }

    /**
     * Closes the file, and rolls nothing.
     *
     * A shutdown is not a period boundary, and treating it as one gives a service that restarts often
     * a directory of one-line files and a retention window measured in deploys. The next start
     * appends to the same file and keeps its clock, which is what [Sink.openedAt] reads off the
     * filesystem for.
     */
    override fun close() = guard.once { sink.withLock { it.out.close() } }

    private fun open(): Sink {
        path.parent?.let(Files::createDirectories)
        val existing = path.exists()
        return Sink(
            out = BufferedOutputStream(Files.newOutputStream(path, CREATE, APPEND)),
            size = if (existing) path.fileSize() else 0,
            // The last line written, which is the best a filesystem reliably offers: a process that
            // was down over midnight rolls on its first line back, carrying a few of yesterday's.
            openedAt = if (existing) Instant.fromEpochMilliseconds(path.getLastModifiedTime().toMillis()) else clock.now(),
        )
    }

    /**
     * The open file and what is known about it, mutated only under [sink]'s lock.
     *
     * A lock rather than the pipeline's single-consumer guarantee, because [close] is called by
     * whoever owns the telemetry — a DI container, a shutdown hook, another thread — while a batch
     * may be halfway into the stream.
     */
    private inner class Sink(
        var out: OutputStream,
        var size: Long,
        var openedAt: Instant,
    ) {
        fun write(lines: List<ByteArray>) {
            if (guard.isClosed) return
            // One reading for the batch. A period boundary crossed between two lines of the same
            // batch is a boundary crossed within a fraction of a second, and paying a clock call per
            // signal to put those lines in the other file buys nobody anything.
            val now = clock.now()
            for (line in lines) {
                if (rotation.due(size, openedAt, now)) roll(now)
                out.write(line)
                out.write(NEWLINE)
                size += line.size + 1
            }
            out.flush()
        }

        private fun roll(now: Instant) {
            out.close()
            rotation.roll(now)
            out = BufferedOutputStream(Files.newOutputStream(path, CREATE, APPEND))
            size = 0
            openedAt = now
        }
    }

    private companion object {
        val NEWLINE = "\n".toByteArray()
    }
}
