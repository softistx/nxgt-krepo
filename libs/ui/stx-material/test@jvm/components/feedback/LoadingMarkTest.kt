package com.softistx.material.components.feedback

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.PixelGrid
import com.softistx.material.contrast
import com.softistx.material.foundation.theme.DefaultSeed
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.stxColorScheme
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThan as intGreater

private val scheme = stxColorScheme(DefaultSeed, isDark = false)

private fun <T> markProbe(
    bounds: NodeBounds,
    contained: Boolean,
    progress: Float? = null,
    block: PixelGrid.() -> T,
): T =
    renderProbe(
        width = 120,
        height = 120,
        content = { StxTheme(isDark = false) { LoadingMark(Modifier.padding(10.dp).probed(bounds), progress, contained) } },
        block = { settle().block() },
    )

/** The contained mark seats the polygon on a disc; the bare mark paints the polygon alone. */
class LoadingMarkTest :
    FeatureSpec({
        feature("the container") {
            scenario("the bare mark paints no container colour, whatever its progress") {
                listOf(null, 0.5f).forEach { progress ->
                    markProbe(NodeBounds(), contained = false, progress = progress) {
                        count(scheme.primaryContainer) shouldBe 0
                        count(scheme.primary) intGreater 100
                    }
                }
            }

            scenario("the contained mark paints a primaryContainer disc under an onPrimaryContainer polygon, whatever its progress") {
                listOf(null, 0.5f).forEach { progress ->
                    markProbe(NodeBounds(), contained = true, progress = progress) {
                        count(scheme.primaryContainer) intGreater 400
                        count(scheme.onPrimaryContainer) intGreater 100
                        count(scheme.primary) shouldBe 0
                    }
                }
            }

            scenario("the disc is round, not a square: its corner shows the page while its rim is painted") {
                val bounds = NodeBounds()
                markProbe(bounds, contained = true) {
                    isFilled(bounds, Corner.TopLeft, inset = 8) shouldBe true
                    isTransparent(bounds, Corner.TopLeft) shouldBe true
                    isTransparent(bounds, Corner.BottomRight) shouldBe true
                }
            }

            scenario("the polygon holds 3:1 on its disc, read off the rendered pixels") {
                val bounds = NodeBounds()
                markProbe(bounds, contained = true, progress = 0.5f) {
                    val ink = pixelAt(bounds, Corner.Center)
                    val disc = pixelAt(bounds, Corner.TopLeft, inset = 8)
                    ink shouldBe scheme.onPrimaryContainer
                    disc shouldBe scheme.primaryContainer
                    contrast(ink, disc) shouldBeGreaterThan 3.0
                }
            }
        }
    })
