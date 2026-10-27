package com.softistx.material.school

import com.softistx.material.school.session.SchoolScreen
import com.softistx.material.school.session.SchoolStart
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import java.io.File

/** One face of the bench: a screen (or an opened detail view) at a size, in a theme. */
data class BenchShot(
    val name: String,
    val screen: SchoolScreen,
    val start: SchoolStart,
    val viewport: Viewport,
    val dark: Boolean,
) {
    val theme: String get() = if (dark) "dark" else "light"
    val file: String get() = "$name-${viewport.name.lowercase()}-$theme.png"
}

/** The names of the views the bench shows, in contact-sheet order: the six screens, then two detail views. */
private fun benchViews(): List<Triple<String, SchoolScreen, SchoolStart>> =
    SchoolScreen.entries.map { Triple(it.name.lowercase(), it, SchoolStart()) } +
        listOf(
            Triple("student-detail", SchoolScreen.Students, SchoolStart(studentId = 3)),
            Triple("thread", SchoolScreen.Messages, SchoolStart(conversationId = 1)),
        )

/** Every shot of the bench; [filter] keeps those whose file name contains one of its comma-separated parts. */
fun benchShots(filter: String? = null): List<BenchShot> {
    val parts = filter?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }.orEmpty()
    return Viewport.entries.flatMap { viewport ->
        benchViews().flatMap { (name, screen, start) ->
            listOf(false, true).map { BenchShot(name, screen, start, viewport, it) }
        }
    }.filter { shot -> parts.isEmpty() || parts.any { it in shot.file } }
}

/** Renders the bench into [dir]: the PNGs, `manifest.json` and one `contact-<viewport>.png` per viewport. */
fun writeBench(
    dir: File,
    shots: List<BenchShot>,
) {
    dir.mkdirs()
    val frames = HashMap<BenchShot, Image>()
    try {
        shots.forEach { shot ->
            val image = renderSchool(shot.screen, shot.viewport, shot.dark, shot.start)
            File(dir, shot.file).writeBytes(png(image))
            frames[shot] = image
        }
        File(dir, "manifest.json").writeText(manifest(shots))
        shots.groupBy { it.viewport }.forEach { (viewport, group) ->
            contactSheet(viewport, group, frames).use {
                File(dir, "contact-${viewport.name.lowercase()}.png").writeBytes(png(it))
            }
        }
    } finally {
        frames.values.forEach { it.close() }
    }
}

private fun manifest(shots: List<BenchShot>): String =
    shots.joinToString(prefix = "[\n", postfix = "\n]\n", separator = ",\n") {
        """  {"file": "${it.file}", "screen": "${it.name}", "viewport": "${it.viewport.name.lowercase()}", """ +
            """"theme": "${it.theme}", "size": "${it.viewport.width}x${it.viewport.height}"}"""
    }

/** Pairs shown per row, and the width of one frame in the sheet, so each sheet stays legible at its own size. */
private fun layout(viewport: Viewport): Pair<Int, Int> =
    when (viewport) {
        Viewport.Phone -> 4 to 230
        Viewport.Tablet -> 4 to 300
        Viewport.Desktop -> 2 to 560
    }

private const val Gap = 16
private const val LabelHeight = 36

/** A grid of the viewport's shots: one pair (light, dark) per cell, labelled with the screen name. */
private fun contactSheet(
    viewport: Viewport,
    shots: List<BenchShot>,
    frames: Map<BenchShot, Image>,
): Image {
    val (perRow, frameWidth) = layout(viewport)
    val frameHeight = frameWidth * viewport.height / viewport.width
    val names = shots.map { it.name }.distinct()
    val rows = (names.size + perRow - 1) / perRow
    val cellWidth = 2 * frameWidth + Gap
    val cellHeight = LabelHeight + frameHeight
    val width = perRow * cellWidth + (perRow + 1) * Gap
    val height = rows * (cellHeight + Gap) + Gap
    val typeface = labelTypeface()
    try {
        Font(typeface, 22f).use { font ->
            Paint().apply { color = Color.makeRGB(30, 30, 36) }.use { ink ->
                Surface.makeRasterN32Premul(width, height).use { surface ->
                    val canvas = surface.canvas
                    canvas.clear(Color.makeRGB(236, 236, 240))
                    names.forEachIndexed { index, name ->
                        val x = Gap + (index % perRow) * (cellWidth + Gap)
                        val y = Gap + (index / perRow) * (cellHeight + Gap)
                        canvas.drawString("$name  (${viewport.name.lowercase()}, light | dark)", x.toFloat(), y + 24f, font, ink)
                        listOf(false, true).forEachIndexed { column, dark ->
                            val shot = shots.firstOrNull { it.name == name && it.dark == dark }
                            val image = shot?.let(frames::get) ?: return@forEachIndexed
                            canvas.drawFrame(image, x + column * (frameWidth + Gap), y + LabelHeight, frameWidth, frameHeight)
                        }
                    }
                    return surface.makeImageSnapshot()
                }
            }
        }
    } finally {
        typeface?.close()
    }
}

private fun Canvas.drawFrame(
    image: Image,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) {
    val source = Rect.makeWH(image.width.toFloat(), image.height.toFloat())
    val target = Rect.makeXYWH(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
    drawImageRect(image, source, target)
}

/**
 * A system sans-serif for the labels: the first family this host has, or the manager's default.
 * Null is deliberate: a host with no fonts at all gets blank labels, and the bench still runs.
 */
private fun labelTypeface(): Typeface? =
    listOf("Helvetica", "Arial", "DejaVu Sans", "Liberation Sans", "Segoe UI")
        .firstNotNullOfOrNull { FontMgr.default.matchFamilyStyle(it, FontStyle.NORMAL) }
        ?: FontMgr.default.matchFamilyStyle(null, FontStyle.NORMAL)
