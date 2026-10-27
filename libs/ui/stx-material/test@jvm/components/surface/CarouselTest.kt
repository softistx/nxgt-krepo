package com.softistx.material.components.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.style.Style
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    width: Int = 400,
    block: (PixelGrid) -> Unit,
) = renderProbe(width = width, height = 200, content = {
    StxTheme(isDark = false) {
        Carousel(count = 3, modifier = Modifier.fillMaxWidth(), peek = peek, style = style) {
            Box(Modifier.fillMaxWidth().height(100.dp).background(page))
        }
    }
}) { block(settle()) }

/** The lengths of the runs of page colour along the scanline [y]: one per visible page. */
private fun PixelGrid.runs(
    y: Int,
    width: Int = 400,
): List<Int> {
    val lengths = mutableListOf<Int>()
    var run = 0
    for (x in 0 until width) {
        if (pixelAt(x, y) == page) {
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

        feature("a narrow container") {
            scenario("the large page shrinks to leave the peek and the gap, at any width") {
                probe(width = 240) { grid ->
                    val runs = grid.runs(y = 50, width = 240)
                    runs[0] shouldBeInRange 180..184
                    runs[1] shouldBeInRange 46..48
                }
            }
        }

        feature("the page is masked") {
            scenario("a corner of the large page is cut by the rounded mask, its middle is the content") {
                probe { grid ->
                    withClue("corner") { (grid.pixelAt(0, 0) == page) shouldBe false }
                    withClue("middle") { grid.pixelAt(100, 50) shouldBe page }
                    withClue("edge, below the corner arc") { grid.pixelAt(0, 50) shouldBe page }
                }
            }
        }

        feature("style") {
            scenario("style dresses the carousel as a whole and the pages stay on top of it") {
                probe(style = painted) { grid ->
                    withClue("gap between the pages") { grid.pixelAt(grid.runs(y = 50)[0] + 4, 50).isPainted() shouldBe true }
                    withClue("page") { grid.pixelAt(100, 50) shouldBe page }
                }
                probe { grid -> grid.count { it.isPainted() } shouldBe 0 }
            }
        }

        feature("the content") {
            scenario("the page content is composed") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) { Carousel(count = 2) { Text("Class ${it + 1}") } }
                    }
                    onNodeWithText("Class 1").assertExists()
                }
            }
        }

        feature("semantics") {
            scenario("M3 reports the collection (1 row, count columns); each page reports its index; the dots are hidden") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            Carousel(count = 3, modifier = Modifier.testTag("carousel")) { Text("Class ${it + 1}") }
                        }
                    }
                    val key = SemanticsProperties.CollectionInfo
                    val info = onNode(SemanticsMatcher.keyIsDefined(key)).fetchSemanticsNode().config[key]
                    withClue("rows") { info.rowCount shouldBe 1 }
                    withClue("columns") { info.columnCount shouldBe 3 }
                    val itemKey = SemanticsProperties.CollectionItemInfo
                    val indexes =
                        onAllNodes(SemanticsMatcher.keyIsDefined(itemKey), useUnmergedTree = true)
                            .fetchSemanticsNodes()
                            .map { it.config[itemKey].columnIndex }
                    indexes.toSet() shouldBe setOf(0, 1, 2)
                    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility), useUnmergedTree = true)
                        .assertCountEquals(1)
                }
            }
        }

        feature("the dots") {
            scenario("the 8 dp dot is the first at rest and the second after a swipe left") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            Carousel(count = 3, modifier = Modifier.testTag("carousel")) {
                                Box(Modifier.fillMaxWidth().height(100.dp).background(page))
                            }
                        }
                    }

                    fun dotWidths(): List<Int> {
                        val image = onRoot().captureToImage().toPixelMap()
                        val widths = mutableListOf<Int>()
                        var run = 0
                        for (x in 0 until image.width) {
                            val ink = (100 until image.height).any { image[x, it] != Color.White && image[x, it].alpha > 0f }
                            if (ink) run++ else if (run > 0) widths += run.also { run = 0 }
                        }
                        return widths
                    }
                    withClue("at rest") { dotWidths().let { it.indexOf(it.max()) } shouldBe 0 }
                    onNodeWithTag("carousel").performTouchInput {
                        swipeLeft(startX = width * 0.8f, endX = width * 0.3f, durationMillis = 1000)
                    }
                    waitForIdle()
                    withClue("after a swipe") { dotWidths().let { it.indexOf(it.max()) } shouldBe 1 }
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
