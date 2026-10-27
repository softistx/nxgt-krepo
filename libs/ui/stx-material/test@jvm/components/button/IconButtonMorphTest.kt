package com.softistx.material.components.button

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.PixelGrid
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import androidx.compose.material3.ToggleButton as MaterialToggleButton

/**
 * The icon buttons, toggles, split button and FAB answer the finger with their corners: round at
 * rest, squarer pressed, and squarer again while checked. Proved on the painted corner pixel, a few
 * pixels in, where a circle has no ink and a squarer corner has. M3's 48 dp touch target is taken
 * away so that the node's corner is the container's corner.
 */
class IconButtonMorphTest :
    FeatureSpec({
        feature("an icon button's corner") {
            listOf(ButtonVariant.Filled, ButtonVariant.Tonal, ButtonVariant.Outlined).forEach { variant ->
                scenario("${variant.name} is round at rest, squarer pressed, round again released") {
                    val bounds = NodeBounds()
                    renderProbe(content = {
                        flat {
                            IconButton(
                                StxIcons.Add,
                                "Add",
                                onClick = {},
                                variant = variant,
                                modifier = Modifier.padding(20.dp).probed(bounds),
                            )
                        }
                    }) {
                        settle().isTransparent(bounds, Corner.TopLeft, inset = INSET) shouldBe true
                        press(bounds).isFilled(bounds, Corner.TopLeft, inset = INSET) shouldBe true
                        release(bounds).isTransparent(bounds, Corner.TopLeft, inset = INSET) shouldBe true
                    }
                }
            }
        }

        feature("an icon toggle's corner") {
            scenario("is round off, squarer checked, and squarer pressed") {
                fun squarer(
                    checked: Boolean,
                    pressed: Boolean,
                ) = cornerFilled(checked, pressed) { isChecked, modifier ->
                    IconToggle(StxIcons.Add, "Pin", isChecked, {}, variant = ButtonVariant.Filled, modifier = modifier)
                }
                squarer(checked = false, pressed = false) shouldBe false
                squarer(checked = true, pressed = false) shouldBe true
                squarer(checked = false, pressed = true) shouldBe true
            }
        }

        feature("a toggle button's corner") {
            scenario("is a pill off, squarer checked, and squarer pressed") {
                fun squarer(
                    checked: Boolean,
                    pressed: Boolean,
                ) = cornerFilled(checked, pressed) { isChecked, modifier ->
                    ToggleButton("Follow", isChecked, {}, variant = ButtonVariant.Filled, modifier = modifier)
                }
                squarer(checked = false, pressed = false) shouldBe false
                squarer(checked = true, pressed = false) shouldBe true
                squarer(checked = false, pressed = true) shouldBe true
            }
        }

        feature("a split button's corners") {
            // Both halves share one bounding box, so the painted ink of the whole is what a corner change moves.
            scenario("each half's inner corners round under the finger, and the trailing half changes when the menu opens") {
                val bounds = NodeBounds()
                renderProbe(width = 300, content = {
                    flat {
                        SplitButton(
                            "Save",
                            onClick = {},
                            overflow = listOf(MenuItem("Save as", onClick = {})),
                            modifier = Modifier.padding(20.dp).probed(bounds),
                        )
                    }
                }) {
                    val rest = settle().ink(bounds)
                    val leadingAt = Offset(bounds.rect.left + 20f, bounds.rect.center.y)
                    val trailingAt = Offset(bounds.rect.right - 20f, bounds.rect.center.y)
                    val leading = press(leadingAt).ink(bounds)
                    release(leadingAt)
                    val trailing = press(trailingAt).ink(bounds)
                    val open = release(trailingAt).ink(bounds)
                    (leading < rest) shouldBe true
                    (trailing < rest) shouldBe true
                    (open != rest) shouldBe true
                }
            }
        }

        feature("a floating action button's corner") {
            // A FAB casts a shadow, so "nothing painted" is not the test: the corner is container-coloured or it is not.
            scenario("is a rounded square at rest, squarer pressed, and back on release") {
                val bounds = NodeBounds()
                renderProbe(content = {
                    flat { Fab(StxIcons.Add, "Add", onClick = {}, modifier = Modifier.padding(20.dp).probed(bounds)) }
                }) {
                    settle().cornerHoldsContainer(bounds) shouldBe false
                    press(bounds).cornerHoldsContainer(bounds) shouldBe true
                    release(bounds).cornerHoldsContainer(bounds) shouldBe false
                }
            }

            scenario("the extended one morphs too") {
                val bounds = NodeBounds()
                renderProbe(width = 300, content = {
                    flat {
                        Fab(StxIcons.Add, "Add", onClick = {}, text = "Compose", modifier = Modifier.padding(20.dp).probed(bounds))
                    }
                }) {
                    settle().cornerHoldsContainer(bounds) shouldBe false
                    press(bounds).cornerHoldsContainer(bounds) shouldBe true
                }
            }
        }

        feature("the defaults keep the geometry they had") {
            scenario("an icon button is the 40 dp container M3's non-morphing overload drew") {
                val ours = size { IconButton(StxIcons.Add, "Add", onClick = {}, variant = ButtonVariant.Filled, modifier = it) }
                val old =
                    size {
                        FilledIconButton(onClick = {}, shape = IconButtonDefaults.filledShape, modifier = it) {
                            Icon(StxIcons.Add, description = "Add")
                        }
                    }
                ours shouldBe old
                ours shouldBe (40 to 40)
            }

            scenario("an icon toggle is the same size as M3's non-morphing overload") {
                val ours =
                    size {
                        IconToggle(
                            StxIcons.Add,
                            "Pin",
                            checked = false,
                            onCheckedChange = {},
                            variant = ButtonVariant.Filled,
                            modifier = it,
                        )
                    }
                val old =
                    size {
                        FilledIconToggleButton(
                            checked = false,
                            onCheckedChange = {},
                            shape = IconButtonDefaults.filledShape,
                            modifier = it,
                        ) {
                            Icon(StxIcons.Add, description = "Pin")
                        }
                    }
                ours shouldBe old
            }

            scenario("a toggle button is as tall as M3's default one") {
                val ours =
                    size(width = 300) {
                        ToggleButton("Follow", checked = false, onCheckedChange = {}, variant = ButtonVariant.Filled, modifier = it)
                    }
                val old =
                    size(width = 300) {
                        MaterialToggleButton(checked = false, onCheckedChange = {}, modifier = it) {
                            Text("Follow", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                ours.second shouldBe old.second
                ours.second shouldBe ButtonDefaults.MinHeight.value.toInt()
            }

            scenario("a split button is as high as M3's default one") {
                val ours = size(width = 300) { SplitButton("Save", onClick = {}, overflow = emptyList(), modifier = it) }
                val old =
                    size(width = 300) {
                        SplitButtonLayout(
                            modifier = it,
                            leadingButton = { SplitButtonDefaults.LeadingButton(onClick = {}) { Text("Save") } },
                            trailingButton = {
                                SplitButtonDefaults.TrailingButton(onClick = {}) { Icon(StxIcons.ChevronDown, description = null) }
                            },
                        )
                    }
                ours.second shouldBe old.second
                ours.second shouldBe SplitButtonDefaults.SmallContainerHeight.value.toInt()
            }

            scenario("a FAB is 56 by 56, and the extended one as high as M3's") {
                val ours = size { Fab(StxIcons.Add, "Add", onClick = {}, modifier = it) }
                val old = size { FloatingActionButton(onClick = {}, modifier = it) { Icon(StxIcons.Add, description = "Add") } }
                ours shouldBe old
                ours shouldBe (56 to 56)
                val extended = size(width = 300) { Fab(StxIcons.Add, "Add", onClick = {}, text = "Compose", modifier = it) }
                val oldExtended =
                    size(width = 300) {
                        ExtendedFloatingActionButton(onClick = {}, modifier = it) { Icon(StxIcons.Add, description = "Add") }
                    }
                extended.second shouldBe oldExtended.second
            }
        }
    })

private const val INSET = 4
private const val FAB_INSET = 3

/** [content] in the light theme, without M3's 48 dp touch target around it. */
@Composable
private fun flat(content: @Composable () -> Unit) {
    StxTheme(isDark = false) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) { content() }
    }
}

/** Whether the top-left corner of a [content] control, in the given state, holds paint. */
private fun cornerFilled(
    checked: Boolean,
    pressed: Boolean,
    content: @Composable (Boolean, Modifier) -> Unit,
): Boolean {
    val bounds = NodeBounds()
    return renderProbe(width = 300, content = {
        flat { content(checked, Modifier.padding(20.dp).probed(bounds)) }
    }) {
        val grid = settle()
        (if (pressed) press(bounds) else grid).isFilled(bounds, Corner.TopLeft, inset = INSET)
    }
}

/** The painted (width, height) of [content] in dp, at density 1. */
private fun size(
    width: Int = 200,
    content: @Composable (Modifier) -> Unit,
): Pair<Int, Int> {
    val bounds = NodeBounds()
    return renderProbe(width = width, height = 200, content = { flat { content(Modifier.probed(bounds)) } }) {
        val grid = settle()
        val r = bounds.rect
        grid.ink(Rect(r.left, r.center.y, r.right, r.center.y + 1)) to grid.ink(Rect(r.center.x, r.top, r.center.x + 1, r.bottom))
    }
}

/** The pixel [FAB_INSET] in from the top-left corner is the container's colour, read off the node's edge-middle. */
private fun PixelGrid.cornerHoldsContainer(node: NodeBounds): Boolean {
    val container = pixelAt(node, Corner.BottomLeft, inset = 8).copy(alpha = 1f)
    return pixelAt(node, Corner.TopLeft, inset = FAB_INSET).copy(alpha = 1f) == container
}
