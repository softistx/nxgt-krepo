package com.softistx.material.components.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.PixelGrid
import com.softistx.material.foundation.motion.StxMotion
import com.softistx.material.foundation.motion.rememberMorphShape
import com.softistx.material.foundation.theme.DefaultSeed
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.stxColorScheme
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private val scheme = stxColorScheme(DefaultSeed, isDark = false)

/** Grid points of a 32 x 32 plate that [shape] contains. */
private fun area(shape: Shape): Int {
    val outline = shape.createOutline(Size(32f, 32f), LayoutDirection.Ltr, Density(1f)) as Outline.Generic
    val skia = outline.path.asSkiaPath()
    var inside = 0
    for (y in 0 until 32) for (x in 0 until 32) if (skia.contains(x + 0.5f, y + 0.5f)) inside++
    return inside
}

private fun <T> markerProbe(
    status: StepStatus,
    bounds: NodeBounds,
    block: PixelGrid.() -> T,
): T =
    renderProbe(
        width = 80,
        height = 80,
        content = {
            StxTheme(isDark = false) {
                Box(Modifier.probed(bounds)) { StepMarker(status, index = 0, connector = false, vertical = true) }
            }
        },
        block = { settle().block() },
    )

/** The stepper's marker: a MaterialShapes plate that morphs with the status. */
@OptIn(ExperimentalTestApi::class)
class StepperMarkerTest :
    FeatureSpec({
        feature("the shape") {
            scenario("a done or upcoming marker is a circle: the corner is clear 3 px in, the edges are filled") {
                listOf(StepStatus.Done, StepStatus.Upcoming).forEach { status ->
                    val bounds = NodeBounds()
                    markerProbe(status, bounds) {
                        isTransparent(bounds, Corner.TopLeft, inset = 3) shouldBe true
                        isTransparent(bounds, Corner.BottomRight, inset = 3) shouldBe true
                        ink(bounds) shouldBeGreaterThan 740
                        ink(bounds) shouldBeLessThan 860
                    }
                }
            }

            scenario("the current marker is the scalloped nine-sided cookie, which covers less than the circle") {
                val bounds = NodeBounds()
                var circle = 0
                markerProbe(StepStatus.Upcoming, bounds) { circle = ink(bounds) }
                markerProbe(StepStatus.Current, bounds) {
                    isTransparent(bounds, Corner.TopLeft, inset = 3) shouldBe true
                    ink(bounds) shouldBeLessThan circle
                    ink(bounds) shouldBeGreaterThan 600
                }
            }

            scenario("the plate is 32 dp and the fill is the status's colour") {
                val bounds = NodeBounds()
                markerProbe(StepStatus.Current, bounds) {
                    bounds.rect.width shouldBe 32f
                    count(scheme.primary) shouldBeGreaterThan 400
                }
                markerProbe(StepStatus.Upcoming, bounds) {
                    count(scheme.surfaceContainerHighest) shouldBeGreaterThan 400
                    count(scheme.primary) shouldBe 0
                }
            }
        }

        feature("the morph") {
            fun runMorph(
                motion: StxMotion,
                midFrame: (circle: Int, cookie: Int, mid: Int) -> Unit,
            ) {
                runComposeUiTest {
                    var target by mutableStateOf(MaterialShapes.Circle)
                    var shape: Shape = RectangleShape
                    mainClock.autoAdvance = false
                    setContent {
                        StxTheme(isDark = false, motion = motion) {
                            shape = rememberMorphShape(target)
                            Box(Modifier.size(32.dp).clip(shape).background(scheme.primary))
                        }
                    }
                    mainClock.advanceTimeBy(100)
                    val circle = area(shape)
                    target = MaterialShapes.Cookie9Sided
                    mainClock.advanceTimeByFrame()
                    mainClock.advanceTimeByFrame()
                    mainClock.advanceTimeBy(48)
                    val mid = area(shape)
                    mainClock.advanceTimeBy(3000)
                    val cookie = area(shape)
                    midFrame(circle, cookie, mid)
                }
            }

            scenario("the outline passes through a shape that is neither the circle nor the cookie") {
                runMorph(StxMotion()) { circle, cookie, mid ->
                    cookie shouldBeLessThan circle
                    mid shouldNotBe circle
                    mid shouldNotBe cookie
                }
            }

            scenario("a marker whose step becomes current is mid-morph a frame later, then the cookie") {
                runComposeUiTest {
                    var status by mutableStateOf(StepStatus.Upcoming)
                    mainClock.autoAdvance = false
                    setContent {
                        StxTheme(isDark = false) {
                            Box(Modifier.testTag("marker")) { StepMarker(status, index = 0, connector = false, vertical = true) }
                        }
                    }
                    fun covered(): Int {
                        val map = onNodeWithTag("marker").captureToImage().toPixelMap()
                        var n = 0
                        for (y in 0 until map.height) for (x in 0 until map.width) if (map[x, y].alpha > 0.5f) n++
                        return n
                    }
                    mainClock.advanceTimeBy(100)
                    val circle = covered()
                    status = StepStatus.Current
                    mainClock.advanceTimeByFrame()
                    mainClock.advanceTimeByFrame()
                    mainClock.advanceTimeBy(48)
                    val mid = covered()
                    mainClock.advanceTimeBy(3000)
                    val cookie = covered()
                    cookie shouldBeLessThan circle
                    mid shouldNotBe circle
                    mid shouldNotBe cookie
                }
            }

            scenario("with motion off the shape snaps to the cookie at once") {
                runMorph(StxMotion(enabled = false)) { circle, cookie, mid ->
                    cookie shouldBeLessThan circle
                    mid shouldBe cookie
                }
            }
        }
    })
