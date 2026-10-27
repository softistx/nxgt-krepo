package com.softistx.material.components.button

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.PixelGrid
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * The shape answers the finger: round at rest, squarer under a press, round again on release.
 * Proved on the painted corner pixel, a few pixels in so that the squarer corner (a radius
 * smaller than the button) has ink there and the pill does not. No spec reads the wall clock:
 * the probe steps frames itself.
 */
@OptIn(ExperimentalTestApi::class)
class ButtonMorphTest :
    FeatureSpec({
        feature("a button's corner") {
            scenario("is a pill at rest, squarer while pressed, and a pill again once released") {
                val bounds = NodeBounds()
                renderProbe(
                    content = {
                        StxTheme(isDark = false) {
                            Button("Save", onClick = {}, modifier = Modifier.padding(20.dp).probed(bounds))
                        }
                    },
                ) {
                    settle().isTransparent(bounds, Corner.TopLeft, inset = INSET) shouldBe true
                    press(bounds).isFilled(bounds, Corner.TopLeft, inset = INSET) shouldBe true
                    release(bounds).isTransparent(bounds, Corner.TopLeft, inset = INSET) shouldBe true
                }
            }
        }

        feature("a button's size") {
            ButtonSize.entries.forEach { size ->
                scenario("${size.name} is as tall as M3's matching height") {
                    val bounds = NodeBounds()
                    renderProbe(
                        width = 400,
                        height = 260,
                        content = {
                            StxTheme(isDark = false) {
                                Button("Save", onClick = {}, size = size, modifier = Modifier.probed(bounds))
                            }
                        },
                    ) {
                        paintedHeight(settle(), bounds) shouldBe size.height.value.toInt()
                    }
                }
            }

            scenario("the five heights are the M3 defaults") {
                ButtonSize.ExtraSmall.height shouldBe ButtonDefaults.ExtraSmallContainerHeight
                ButtonSize.Small.height shouldBe ButtonDefaults.MinHeight
                ButtonSize.Medium.height shouldBe ButtonDefaults.MediumContainerHeight
                ButtonSize.Large.height shouldBe ButtonDefaults.LargeContainerHeight
                ButtonSize.ExtraLarge.height shouldBe ButtonDefaults.ExtraLargeContainerHeight
            }
        }

        feature("the collapsed responsive button") {
            scenario("stays 40 by 40 whatever the size asked for the expanded form") {
                val bounds = NodeBounds()
                renderProbe(
                    content = {
                        StxTheme(isDark = false) {
                            ResponsiveButton(
                                text = "Add",
                                icon = StxIcons.Add,
                                onClick = {},
                                collapseBelow = 400.dp,
                                size = ButtonSize.Medium,
                                modifier = Modifier.probed(bounds),
                            )
                        }
                    },
                ) {
                    val grid = settle()
                    paintedHeight(grid, bounds) shouldBe 40
                    paintedWidth(grid, bounds) shouldBe 40
                }
            }
        }

        feature("a button's semantics") {
            scenario("keeps Role.Button") {
                runComposeUiTest {
                    setContent { StxTheme(isDark = false) { Button("Save", onClick = {}, size = ButtonSize.Large) } }
                    onNodeWithText("Save").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                }
            }
        }
    })

private const val INSET = 4

/**
 * The painted container's height down the node's centre line. The node's own bounds include M3's
 * 48 dp touch target, which is larger than what is drawn.
 */
private fun paintedHeight(
    grid: PixelGrid,
    node: NodeBounds,
): Int {
    val r = node.rect
    return grid.ink(Rect(r.center.x, r.top, r.center.x + 1, r.bottom))
}

/** The painted container's width along the node's centre line. */
private fun paintedWidth(
    grid: PixelGrid,
    node: NodeBounds,
): Int {
    val r = node.rect
    return grid.ink(Rect(r.left, r.center.y, r.right, r.center.y + 1))
}
