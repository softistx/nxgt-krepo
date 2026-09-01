package com.softistx.material.button

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.icon.StxIcons
import com.softistx.material.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import kotlin.math.abs

/**
 * The collapsed form has to *be* an icon button, not a pill with the label removed — a stadium
 * holding one glyph reads as a button whose text failed to load. That is a claim about painted
 * pixels, so it is measured in painted pixels: the container is rendered and its bounding box
 * compared against itself.
 *
 * Rendered with `ImageComposeScene`, like `ChipHoverTest` — no window, milliseconds, headless.
 */
class ResponsiveButtonTest :
    FeatureSpec({
        feature("the collapsed responsive button") {
            scenario("is as tall as it is wide, the way an icon button is") {
                // Measured: 40 x 40, which is M3's icon button exactly. The pill it used to be was
                // 68 x 40 — the label's padding stayed behind when the label left.
                abs(aspectAt(width = 120.dp) - 1.0) shouldBeLessThan 0.12
            }

            scenario("is not merely the expanded one with its label taken out") {
                // The same button given room: 130 x 40.
                aspectAt(width = 420.dp) shouldBeGreaterThan 2.0
            }
        }
    })

/** The painted container's width ÷ its height, when the button is offered [width]. */
private fun aspectAt(width: Dp): Double {
    val scene = ImageComposeScene(width = 500, height = 160)
    try {
        scene.setContent {
            StxTheme(isDark = false) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(Inset)) {
                    ResponsiveButton(
                        text = "Add item",
                        icon = StxIcons.Add,
                        onClick = {},
                        modifier = Modifier.width(width),
                    )
                }
            }
        }
        var time = 0L
        repeat(SETTLE_FRAMES) {
            time += FRAME_NANOS
            scene.render(time)
        }
        val map = scene.render(time).toComposeImageBitmap().toPixelMap()
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var top = Int.MAX_VALUE
        var bottom = Int.MIN_VALUE
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                // Anything that is not the white page is the button: the container is filled.
                if (map[x, y] != Color.White) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return (right - left + 1).toDouble() / (bottom - top + 1).toDouble()
    } finally {
        scene.close()
    }
}

private val Inset = 20.dp
private const val SETTLE_FRAMES = 8
private const val FRAME_NANOS = 100_000_000L
