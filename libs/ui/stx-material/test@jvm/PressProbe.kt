package com.softistx.material

import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.theme.StxTheme

/** The pixels of one scanline: the left edge at rest, and under the pointer at 1, 3 and 8 px in. */
class Scanline(
    val rest: Color,
    val pressed: Map<Int, Color>,
) {
    /** True when nothing is drawn [x] px in while pressed: the node has scaled away from that pixel. */
    fun pressedBare(x: Int): Boolean = pressed.getValue(x) == Color.White

    /** True when something is drawn [x] px in while pressed. */
    fun pressedFilled(x: Int): Boolean = !pressedBare(x)
}

/**
 * [part] at 400 dp wide on white, pressed at [pressAt] and read along the scanline [y] of its bounds.
 * At 1 px per dp a scale s moves the edge in by 400 x (1 - s) / 2: 2 px for 0.99, 6 px for 0.97, so
 * the pixel 3 px in is filled for 0.99 and bare for 0.97, and the pixel 8 px in is filled for both.
 * Read a colour for what the edge shows, not only the red of a painted style: a container paints
 * over a style's background, and the ripple darkens a pixel but never turns white into anything else.
 */
fun scanline(
    pressAt: (NodeBounds) -> Offset = { it.rect.center },
    y: (NodeBounds) -> Float = { it.rect.center.y },
    part: @Composable (Modifier) -> Unit,
): Scanline {
    val bounds = NodeBounds()
    return renderProbe(width = 400, height = 200, content = {
        StxTheme(strings = StxStrings.En, isDark = false) { part(Modifier.probed(bounds)) }
    }) {
        val first = settle()
        val at = { x: Int -> NodeBounds().also { it.rect = Rect(x.toFloat(), y(bounds), x + 1f, y(bounds) + 1f) } }
        val down = press(pressAt(bounds))
        Scanline(first.pixelAt(at(0), Corner.TopLeft), listOf(1, 3, 8).associateWith { down.pixelAt(at(it), Corner.TopLeft) })
    }
}

/** The pixels [part] draws on white at 400 x 300 when handed [style]: `Style { alpha(0f) }` leaves none. */
fun inkWith(
    style: Style,
    part: @Composable (Style) -> Unit,
): Int =
    renderProbe(width = 400, height = 300, content = {
        StxTheme(strings = StxStrings.En, isDark = false) { part(style) }
    }) { settle().ink() }

/** A style that hides the node, so a spec can see whether a `style` parameter reached it. */
val hidden = Style { alpha(0f) }

/** How many pixels of [part] come out red when it is handed [painted]: a node the style reached shows red. */
fun redPixels(part: @Composable (Style) -> Unit): Int =
    renderProbe(width = 400, height = 300, content = { StxTheme(strings = StxStrings.En, isDark = false) { part(painted) } }) {
        settle().count { it.isPainted() }
    }
