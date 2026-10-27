package com.softistx.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path

/** The corners of a node's bounds, for [RenderProbe.pixelAt]. */
enum class Corner { TopLeft, TopRight, BottomLeft, BottomRight, Center }

/** Where a node ended up, filled in by [probed] once the node has been laid out. */
class NodeBounds {
    var rect: Rect = Rect.Zero
        internal set
}

/** Records this node's bounds in the root into [into], so a spec can aim at it by pixel. */
fun Modifier.probed(into: NodeBounds): Modifier = onGloballyPositioned { into.rect = it.boundsInRoot() }

/**
 * A headless render of [content] that a spec can read pixels from and poke with a pointer, so a
 * change to how a component looks is proved in pixels instead of asserted from its parameters.
 *
 * It renders at density 1, so a `dp` and a pixel are the same number. [background] fills the whole
 * scene: "transparent" below means "shows [background]", and "ink" is any pixel that is not it.
 * Close it, or use [renderProbe], which does.
 */
class RenderProbe(
    width: Int,
    height: Int,
    private val background: Color = Color.White,
    content: @Composable () -> Unit,
) : AutoCloseable {
    private val scene = ImageComposeScene(width = width, height = height, density = Density(1f))
    private var time = 0L

    init {
        scene.setContent { Box(Modifier.fillMaxSize().background(background)) { content() } }
    }

    /**
     * Renders until animations have settled and answers the last frame. Every state in this
     * library animates, so one frame would read a value on its way rather than where it ends.
     */
    fun settle(): PixelGrid {
        repeat(SETTLE_FRAMES) {
            time += FRAME_NANOS
            scene.render(time)
        }
        return PixelGrid(scene.render(time).toComposeImageBitmap().toPixelMap(), background)
    }

    /** Presses (and holds) the pointer at [at] — a move first, so hover state is entered — then settles. */
    fun press(at: Offset): PixelGrid {
        scene.sendPointerEvent(PointerEventType.Enter, at)
        scene.sendPointerEvent(PointerEventType.Move, at)
        scene.sendPointerEvent(PointerEventType.Press, at)
        return settle()
    }

    /** Presses the middle of [node]. */
    fun press(node: NodeBounds): PixelGrid = press(node.rect.center)

    /** Lifts the pointer where [press] left it, then settles. */
    fun release(at: Offset): PixelGrid {
        scene.sendPointerEvent(PointerEventType.Release, at)
        return settle()
    }

    /** Lifts the pointer from the middle of [node]. */
    fun release(node: NodeBounds): PixelGrid = release(node.rect.center)

    /**
     * Settles, then writes the frame to [path] as a PNG and answers the path, so a reader (or an
     * agent) can open the picture and judge hierarchy, accent and surfaces instead of inferring
     * them from code. Parent folders are created.
     */
    fun savePng(path: Path): Path {
        settle()
        val png = checkNotNull(scene.render(time).encodeToData(EncodedImageFormat.PNG)) { "PNG encoding failed" }
        path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.write(path, png.bytes)
        return path
    }

    override fun close() = scene.close()

    private companion object {
        const val SETTLE_FRAMES = 8
        const val FRAME_NANOS = 100_000_000L
    }
}

/** Runs [block] against a [RenderProbe] and closes it afterwards. */
fun <T> renderProbe(
    width: Int = 200,
    height: Int = 200,
    background: Color = Color.White,
    content: @Composable () -> Unit,
    block: RenderProbe.() -> T,
): T = RenderProbe(width, height, background, content).use { it.block() }

/** One rendered frame, with the questions a spec asks of it. */
class PixelGrid(
    private val map: PixelMap,
    private val background: Color,
) {
    /** The pixel [inset] pixels inside [corner] of [node]'s bounds (the centre ignores [inset]). */
    fun pixelAt(
        node: NodeBounds,
        corner: Corner,
        inset: Int = 0,
    ): Color {
        val r = node.rect
        val x =
            when (corner) {
                Corner.TopLeft, Corner.BottomLeft -> r.left + inset
                Corner.TopRight, Corner.BottomRight -> r.right - 1 - inset
                Corner.Center -> r.center.x
            }
        val y =
            when (corner) {
                Corner.TopLeft, Corner.TopRight -> r.top + inset
                Corner.BottomLeft, Corner.BottomRight -> r.bottom - 1 - inset
                Corner.Center -> r.center.y
            }
        return map[x.toInt().coerceIn(0, map.width - 1), y.toInt().coerceIn(0, map.height - 1)]
    }

    /** True when the pixel shows the scene's background, i.e. nothing was painted there. */
    fun isTransparent(
        node: NodeBounds,
        corner: Corner,
        inset: Int = 0,
    ): Boolean = pixelAt(node, corner, inset) == background

    /** True when something was painted at that pixel. */
    fun isFilled(
        node: NodeBounds,
        corner: Corner,
        inset: Int = 0,
    ): Boolean = !isTransparent(node, corner, inset)

    /** Pixels in [region] (the whole frame by default) that are not the background. */
    fun ink(region: Rect? = null): Int {
        val x0 = (region?.left ?: 0f).toInt().coerceIn(0, map.width)
        val x1 = (region?.right ?: map.width.toFloat()).toInt().coerceIn(0, map.width)
        val y0 = (region?.top ?: 0f).toInt().coerceIn(0, map.height)
        val y1 = (region?.bottom ?: map.height.toFloat()).toInt().coerceIn(0, map.height)
        var count = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                if (map[x, y] != background) count++
            }
        }
        return count
    }

    /** Pixels of [node]'s bounds that are not the background. */
    fun ink(node: NodeBounds): Int = ink(node.rect)
}
