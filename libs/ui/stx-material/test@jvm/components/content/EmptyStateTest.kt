package com.softistx.material.components.content

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.DefaultSeed
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.stxColors
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThan as intGreater

/** The summed alpha of the whole rendered root: zero while nothing is visible, growing with a fade. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.opacity(): Long {
    val map = onRoot().captureToImage().toPixelMap()
    var sum = 0L
    for (y in 0 until map.height) for (x in 0 until map.width) sum += (map[x, y].alpha * 255).toLong()
    return sum
}

/** The empty pane: it fades in for real, wears an optional plate, and its title is a heading. */
@OptIn(ExperimentalTestApi::class)
class EmptyStateTest :
    FeatureSpec({
        feature("the enter animation") {
            scenario("the pane is invisible on the first frame, part-way after a few, and fuller once settled") {
                runComposeUiTest {
                    mainClock.autoAdvance = false
                    setContent {
                        StxTheme(
                            strings = StxStrings.En,
                            isDark = false,
                        ) { EmptyState("Nothing here", description = "Come back later.") }
                    }
                    val first = opacity()
                    mainClock.advanceTimeBy(48)
                    val partway = opacity()
                    mainClock.advanceTimeBy(2_000)
                    val settled = opacity()
                    first shouldBe 0L
                    partway shouldBeGreaterThan first
                    settled shouldBeGreaterThan partway
                }
            }
        }

        feature("the icon plate") {
            val soft = stxColors(DefaultSeed, isDark = false).scheme.secondaryContainer

            scenario("a SoftBurst plate in secondaryContainer is a burst, not a square, and absent without the slot") {
                val bounds = NodeBounds()
                renderProbe(
                    width = 200,
                    height = 200,
                    content = {
                        StxTheme(strings = StxStrings.En, isDark = false) {
                            EmptyStateDefaults.IconPlate(StxIcons.Inbox, Modifier.padding(20.dp).probed(bounds))
                        }
                    },
                ) {
                    val frame = settle()
                    frame.isTransparent(bounds, Corner.TopLeft) shouldBe true
                    frame.isTransparent(bounds, Corner.BottomRight) shouldBe true
                    frame.count(soft) intGreater 3000
                }
                renderProbe(
                    width = 400,
                    height = 300,
                    content = {
                        StxTheme(strings = StxStrings.En, isDark = false) {
                            EmptyState("Nothing here", illustration = { EmptyStateDefaults.IconPlate(StxIcons.Inbox) })
                        }
                    },
                ) {
                    settle().count(soft) intGreater 3000
                }
                renderProbe(
                    width = 400,
                    height = 300,
                    content = { StxTheme(strings = StxStrings.En, isDark = false) { EmptyState("Nothing here") } },
                ) {
                    settle().count(soft) shouldBe 0
                }
            }
        }

        feature("the title") {
            scenario("is announced as a heading") {
                runComposeUiTest {
                    setContent { StxTheme(strings = StxStrings.En) { EmptyState("Nothing here") } }
                    onNodeWithText("Nothing here").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                }
            }
        }
    })
