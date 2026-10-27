package com.softistx.material.foundation.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.softistx.material.NodeBounds
import com.softistx.material.PixelGrid
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeInRange

private const val SIZE = 24
private const val MIN_COVER = 14 // 60% of 24 px, on each axis
private const val TOLERANCE = 0.15

/** The icon, the ink it paints at 24 dp, measured once and held within [TOLERANCE]. */
private val icons: Map<String, Pair<ImageVector, Int>> =
    mapOf(
        "bell" to (StxIcons.Bell to 190),
        "printer" to (StxIcons.Printer to 235),
        "phone" to (StxIcons.Phone to 126),
        "edit-note" to (StxIcons.EditNote to 108),
    )

/** The rows and columns of the 24 px square that hold ink: the icon's bounding box. */
private fun PixelGrid.inkSpan(horizontal: Boolean): List<Int> =
    (0 until SIZE).filter { i ->
        val line = i.toFloat()
        val strip = if (horizontal) Rect(line, 0f, line + 1f, SIZE.toFloat()) else Rect(0f, line, SIZE.toFloat(), line + 1f)
        ink(strip) > 0
    }

/** Each icon is drawn at 24 dp with a real extent on both axes and a stable amount of ink. */
class NewIconsTest :
    FeatureSpec({
        feature("the icons added for the School showcase") {
            icons.forEach { (name, expected) ->
                val (vector, expectedInk) = expected
                scenario("$name has a bounding box over 60% of the square on both axes, and its usual ink") {
                    val bounds = NodeBounds()
                    renderProbe(
                        width = SIZE,
                        height = SIZE,
                        content = {
                            Box(Modifier.probed(bounds)) {
                                Icon(icon = vector, description = null, size = IconSize.Large, tint = Color.Black)
                            }
                        },
                    ) {
                        val frame = settle()
                        val columns = frame.inkSpan(horizontal = true)
                        val rows = frame.inkSpan(horizontal = false)
                        (columns.last() - columns.first() + 1) shouldBeGreaterThanOrEqual MIN_COVER
                        (rows.last() - rows.first() + 1) shouldBeGreaterThanOrEqual MIN_COVER
                        frame.ink() shouldBeInRange
                            (expectedInk * (1 - TOLERANCE)).toInt()..(expectedInk * (1 + TOLERANCE)).toInt()
                    }
                }
            }
        }
    })
