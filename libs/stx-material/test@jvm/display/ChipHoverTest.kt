package com.softistx.material.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import com.softistx.material.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import kotlin.math.abs

/**
 * The only test here that renders. It exists because the hover state layer was measured wrong by
 * eye twice: Material 3's chip has no hover colour at all — `SelectableChipColors` carries a
 * `TODO` where it should be — and once one was added, it read as nothing on a *selected* chip
 * while being obvious on an unselected one.
 *
 * The reason is not a missing state. It is that the selected chip in a real screen is the one that
 * was just clicked, so it already wears Material's focus layer, and a second 8 % layer on top of
 * that is half the change the same 8 % makes over a transparent container. Hence two alphas, and
 * hence this spec: the numbers are not something to hold in one's head.
 *
 * `ImageComposeScene` renders to a bitmap with no window, so this costs milliseconds and runs on a
 * headless host. It is jvm-only — skiko's native library comes from `$compose.desktop.currentOs`.
 */
class ChipHoverTest :
    FeatureSpec({
        feature("the hover state Material 3's chip does not have") {
            scenario("answers on an unselected chip, whose container is otherwise transparent") {
                hoverShift(clickFirst = false) shouldBeGreaterThan VISIBLE_SHIFT
            }

            scenario("answers on a selected chip, over the focus layer the selecting click left behind") {
                hoverShift(clickFirst = true) shouldBeGreaterThan VISIBLE_SHIFT
            }
        }
    })

/**
 * How far the chip's container moves when the pointer arrives, as a mean channel distance in 0..1.
 *
 * [clickFirst] clicks the chip before measuring, which is how a chip becomes selected in a real
 * screen — and which leaves it focused, the case that made the first attempt at this look broken.
 */
private fun hoverShift(clickFirst: Boolean): Float {
    val scene = ImageComposeScene(width = 300, height = 120)
    try {
        scene.setContent {
            StxTheme(isDark = false) {
                var selected by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().background(Color.White).padding(Inset)) {
                    Chip(text = "Paid", selected = selected, onClick = { selected = !selected })
                }
            }
        }
        var time = 0L

        // Every state here animates, so a single frame would read the value on its way rather than
        // where it settled.
        fun settle(): Color {
            repeat(SETTLE_FRAMES) {
                time += FRAME_NANOS
                scene.render(time)
            }
            return scene.render(time).toComposeImageBitmap().toPixelMap()[SAMPLE_X, SAMPLE_Y]
        }

        scene.sendPointerEvent(PointerEventType.Enter, Offset(SAMPLE_X.toFloat(), SAMPLE_Y.toFloat()))
        scene.sendPointerEvent(PointerEventType.Move, Offset(SAMPLE_X + 1f, SAMPLE_Y.toFloat()))
        if (clickFirst) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(SAMPLE_X + 1f, SAMPLE_Y.toFloat()))
            scene.sendPointerEvent(PointerEventType.Release, Offset(SAMPLE_X + 1f, SAMPLE_Y.toFloat()))
            settle()
        }
        val hovered = settle()
        scene.sendPointerEvent(PointerEventType.Exit, Offset(AWAY_X, AWAY_Y))
        val cold = settle()
        return distance(cold, hovered)
    } finally {
        scene.close()
    }
}

private fun distance(
    a: Color,
    b: Color,
): Float = (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)) / 3f

/**
 * Below this the change is in the numbers and not on the screen. It is set where it is on purpose:
 * with Material's plain 8 % layer on the filled container the selected chip shifts by 0.068 and
 * this spec fails, which is exactly the state that looked broken.
 */
private const val VISIBLE_SHIFT = 0.09f

private val Inset = 20.dp
private const val SAMPLE_X = 26
private const val SAMPLE_Y = 36
private const val AWAY_X = 280f
private const val AWAY_Y = 110f
private const val SETTLE_FRAMES = 8
private const val FRAME_NANOS = 100_000_000L
