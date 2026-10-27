package com.softistx.material.components.content

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.DefaultSeed
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.stxColors
import com.softistx.material.renderProbe
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan as intGreater
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

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
                    setContent { StxTheme(isDark = false) { EmptyState("Nothing here", description = "Come back later.") } }
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

            scenario("a SoftBurst plate in secondaryContainer appears with an icon, and not without") {
                renderProbe(
                    width = 400,
                    height = 300,
                    content = { StxTheme(isDark = false) { EmptyState("Nothing here", icon = StxIcons.Inbox) } },
                ) {
                    // 96 dp plate: a burst, so under the 9216 px square and well over a sliver.
                    settle().count(soft) intGreater 3000
                    settle().count(soft) shouldBeLessThan 9216
                }
                renderProbe(
                    width = 400,
                    height = 300,
                    content = { StxTheme(isDark = false) { EmptyState("Nothing here") } },
                ) {
                    settle().count(soft) shouldBe 0
                }
            }

            scenario("an illustration with an icon is refused, by message") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        runComposeUiTest {
                            setContent { StxTheme { EmptyState("x", illustration = {}, icon = StxIcons.Inbox) } }
                        }
                    }
                failure.message shouldBe
                    "EmptyState: illustration and icon both set; the icon plate is the default picture, pass one of them"
            }
        }

        feature("the title") {
            scenario("is announced as a heading") {
                runComposeUiTest {
                    setContent { StxTheme { EmptyState("Nothing here") } }
                    onNodeWithText("Nothing here").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                }
            }
        }
    })
