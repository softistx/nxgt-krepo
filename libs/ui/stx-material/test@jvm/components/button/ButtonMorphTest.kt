package com.softistx.material.components.button

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.PixelGrid
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import androidx.compose.material3.Button as MaterialButton

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
            listOf(ButtonSize.ExtraSmall, ButtonSize.Small, ButtonSize.Large).forEach { size ->
                scenario("${size.name} is a pill at rest, squarer pressed, and a pill again once released") {
                    val bounds = NodeBounds()
                    renderProbe(
                        width = 400,
                        height = 300,
                        content = {
                            StxTheme(isDark = false) {
                                // Without M3's 48 dp touch target the node's corner is the container's corner.
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    Button("Save", onClick = {}, size = size, modifier = Modifier.padding(20.dp).probed(bounds))
                                }
                            }
                        },
                    ) {
                        // Inset a tenth of the height, so the pill is still clear there and the squarer corner is not.
                        val inset = (size.height.value / 10).toInt().coerceAtLeast(3)
                        settle().isTransparent(bounds, Corner.TopLeft, inset = inset) shouldBe true
                        press(bounds).isFilled(bounds, Corner.TopLeft, inset = inset) shouldBe true
                        release(bounds).isTransparent(bounds, Corner.TopLeft, inset = inset) shouldBe true
                    }
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

        feature("a link button") {
            scenario("is as tall at Small as at Large: a link is text in a sentence") {
                // M3's 48 dp touch target would hide the container; take it away to measure it.
                fun height(size: ButtonSize): Float {
                    val bounds = NodeBounds()
                    return renderProbe(
                        content = {
                            StxTheme(isDark = false) {
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    Button(
                                        text = "Docs",
                                        onClick = {},
                                        variant = ButtonVariant.Link,
                                        size = size,
                                        modifier = Modifier.probed(bounds),
                                    )
                                }
                            }
                        },
                    ) {
                        settle()
                        bounds.rect.height
                    }
                }
                height(ButtonSize.Large) shouldBe height(ButtonSize.Small)
                // Measured: M3 keeps its own 40 dp minimum under a link, so Small is where it stops.
                height(ButtonSize.Small) shouldBe ButtonDefaults.MinHeight.value
            }
        }

        feature("the default size") {
            scenario("is as wide as the old M3 content padding made it") {
                fun width(content: @Composable (Modifier) -> Unit): Float {
                    val bounds = NodeBounds()
                    return renderProbe(
                        content = { StxTheme(isDark = false) { content(Modifier.probed(bounds)) } },
                    ) {
                        settle()
                        bounds.rect.width
                    }
                }
                val ours = width { Button("Save changes", onClick = {}, modifier = it) }
                val old =
                    width {
                        MaterialButton(
                            onClick = {},
                            shapes = ButtonDefaults.shapes(),
                            modifier = it,
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) { Text("Save changes", style = ButtonDefaults.textStyleFor(ButtonDefaults.MinHeight)) }
                    }
                ours shouldBe old
            }
        }

        feature("the expanded responsive button") {
            scenario("sets its label at the size's text style, as Button does") {
                fun width(content: @Composable (Modifier) -> Unit): Float {
                    val bounds = NodeBounds()
                    return renderProbe(
                        width = 700,
                        height = 300,
                        content = { StxTheme(isDark = false) { content(Modifier.probed(bounds)) } },
                    ) {
                        settle()
                        bounds.rect.width
                    }
                }
                val responsive =
                    width {
                        Box(it) {
                            ResponsiveButton("Add item", StxIcons.Add, onClick = {}, collapseBelow = 100.dp, size = ButtonSize.Large)
                        }
                    }
                val reference =
                    width {
                        Box(it) {
                            ButtonSurface(onClick = {}, size = ButtonSize.Large) {
                                Icon(StxIcons.Add, description = null, size = IconSize.XLarge)
                                Text("Add item", style = ButtonDefaults.textStyleFor(ButtonSize.Large.height))
                            }
                        }
                    }
                responsive shouldBe reference
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
