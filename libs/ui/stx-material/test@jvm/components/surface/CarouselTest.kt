package com.softistx.material.components.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.style.Style
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.PixelGrid
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.isPainted
import com.softistx.material.painted
import com.softistx.material.renderProbe
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe

private val page = Color.Blue

private fun probe(
    peek: Dp = 48.dp,
    style: Style = Style,
    block: (PixelGrid) -> Unit,
) = renderProbe(width = 400, height = 200, content = {
    StxTheme(isDark = false) {
        Carousel(count = 3, modifier = Modifier.fillMaxWidth(), peek = peek, style = style) {
            Box(Modifier.fillMaxWidth().height(100.dp).background(page))
        }
    }
}) { block(settle()) }

private fun PixelGrid.at(
    x: Int,
    y: Int,
): Color = pixelAt(NodeBounds().also { it.rect = Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f) }, Corner.TopLeft)

/** The lengths of the runs of page colour along the scanline [y]: one per visible page. */
private fun PixelGrid.runs(y: Int): List<Int> {
    val lengths = mutableListOf<Int>()
    var run = 0
    for (x in 0 until 400) {
        if (at(x, y) == page) {
            run++
        } else if (run > 0) {
            lengths += run
            run = 0
        }
    }
    if (run > 0) lengths += run
    return lengths
}

@OptIn(ExperimentalTestApi::class)
class CarouselTest :
    FeatureSpec({
        feature("page widths follow peek") {
            scenario("the large page takes the width left by the peek and the gap, the peeking one is the peek") {
                probe { grid ->
                    val runs = grid.runs(y = 50)
                    runs.size shouldBe 2
                    runs[0] shouldBeInRange 340..344
                    runs[1] shouldBeInRange 46..48
                }
            }

            scenario("a wider peek leaves a narrower large page and a wider peeking one") {
                probe(peek = 80.dp) { grid ->
                    val runs = grid.runs(y = 50)
                    runs[0] shouldBeInRange 308..312
                    runs[1] shouldBeInRange 78..80
                }
            }
        }

        feature("the page is masked") {
            scenario("a corner of the large page is cut by the rounded mask, its middle is the content") {
                probe { grid ->
                    withClue("corner") { (grid.at(0, 0) == page) shouldBe false }
                    withClue("middle") { grid.at(100, 50) shouldBe page }
                    withClue("edge, below the corner arc") { grid.at(0, 50) shouldBe page }
                }
            }
        }

        feature("style") {
            scenario("style dresses the carousel as a whole and the pages stay on top of it") {
                probe(style = painted) { grid ->
                    withClue("gap between the pages") { grid.at(346, 50).isPainted() shouldBe true }
                    withClue("page") { grid.at(100, 50) shouldBe page }
                }
                probe { grid -> grid.count { it.isPainted() } shouldBe 0 }
            }

            scenario("the page content is composed") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) { Carousel(count = 2) { Text("Class ${it + 1}") } }
                    }
                    onNodeWithText("Class 1").assertExists()
                }
            }
        }

        feature("the signature") {
            scenario("signature guard: a trailing page lambda still compiles after style") {
                runComposeUiTest {
                    setContent { StxTheme { Carousel(2, Modifier, 48.dp, Style) { Text("x") } } }
                }
                renderProbe(content = { StxTheme { Carousel(count = 2) { Text("x") } } }) { settle().ink() shouldBeGreaterThan 0 }
            }
        }
    })
