package com.strange.telemetry.export

import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * When [FileExporter] starts a new file, what the old one is called, and which old ones survive.
 *
 * Separate from the exporter because they answer to different things: the exporter owns an open
 * stream and the bytes going into it, this owns a directory and the names in it. The exporter can be
 * read without knowing how a file is pruned, and this can be tested against a directory with no
 * telemetry anywhere near it — which is how its specs are written.
 */
internal class Rotation(
    private val path: Path,
    private val maxSize: Long,
    private val every: Duration?,
    private val keep: Int,
    private val compress: Boolean,
) {
    init {
        require(maxSize >= 0) { "maxSize is a size in bytes, or 0 for no size limit; was $maxSize" }
        require(keep >= 0) { "keep is how many rotated files survive; was $keep" }
        require(every == null || every.inWholeSeconds > 0) {
            "every is the period a file covers and is rounded to whole seconds; $every rounds to none"
        }
    }

    /**
     * Whether the file holding [size] bytes since [openedAt] should be set aside now.
     *
     * An **empty file is never rotated**, whatever the clock says. Without that, an hourly rotation
     * on a service that is quiet overnight produces eight empty files and prunes the eight real ones
     * out of existence behind them — retention counting files that hold nothing is retention that
     * deletes what it was asked to keep.
     */
    fun due(
        size: Long,
        openedAt: Instant,
        now: Instant,
    ): Boolean = size > 0 && ((maxSize > 0 && size >= maxSize) || (every != null && period(openedAt) != period(now)))

    /**
     * The period [at] falls in, counted from the epoch rather than from when the file was opened.
     *
     * That alignment is the difference between a daily rotation and "24 hours after this process
     * started": `24.hours` rolls at UTC midnight and `1.hours` at the top of the hour, so two
     * processes started at different times cut their files at the same moments and a day's telemetry
     * is one file rather than two halves.
     */
    private fun period(at: Instant): Long = at.epochSeconds.floorDiv(every!!.inWholeSeconds)

    /**
     * Moves the active file aside, compresses it if asked, and prunes what is now too old.
     *
     * The name carries the moment of the roll, not the period the file covers — a file that filled up
     * on size covers no period at all, and one timestamp that always means the same thing beats two
     * that mean different things depending on why the roll happened.
     */
    fun roll(now: Instant) {
        if (!path.exists()) return
        val rotated = free(now)
        Files.move(path, rotated)
        if (compress) compress(rotated)
        prune()
    }

    /** Deletes every rotated file beyond the newest [keep]. */
    fun prune() {
        val survivors = existing().sortedDescending()
        survivors.drop(keep).forEach { it.deleteIfExists() }
    }

    /** The rotated files, newest first by name — which is chronological, the stamp being fixed-width. */
    fun existing(): List<Path> {
        val directory = path.parent ?: return emptyList()
        if (!directory.exists()) return emptyList()
        return Files.newDirectoryStream(directory, "$base-*").use { entries ->
            entries.filter { it.name.endsWith(extension) || it.name.endsWith("$extension.gz") }
        }
    }

    /**
     * The first unused name for a roll at [now].
     *
     * A second is a long time when a file is filling on size, so the stamp alone is not unique and
     * the counter is not decoration: without it `Files.move` would overwrite the file it rotated a
     * moment ago, which is the one failure a rotation must not have.
     */
    private fun free(now: Instant): Path {
        val stamp = STAMP.format(java.time.Instant.ofEpochSecond(now.epochSeconds))
        val directory = path.parent ?: Path.of("")
        return generateSequence(0) { it + 1 }
            .map { directory.resolve(if (it == 0) "$base-$stamp$extension" else "$base-$stamp-$it$extension") }
            .first { !it.exists() && !Path.of("$it.gz").exists() }
    }

    private fun compress(file: Path) {
        val target = Path.of("$file.gz")
        Files.newInputStream(file).use { source ->
            GZIPOutputStream(Files.newOutputStream(target)).use(source::transferTo)
        }
        file.deleteIfExists()
    }

    /** `telemetry` of `logs/telemetry.jsonl`. */
    private val base: String get() = path.name.substringBeforeLast('.', path.name)

    /** `.jsonl` of `logs/telemetry.jsonl`, or empty for a name with no extension. */
    private val extension: String get() = path.name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }

    private companion object {
        /**
         * UTC, and no colons.
         *
         * A colon is not a legal filename character on Windows, and a local zone makes the names of a
         * file set jump backwards for an hour every autumn — which breaks the sort that retention is
         * built on, in the one hour a year nobody is watching.
         */
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    }
}
