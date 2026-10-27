package com.softistx.material.components.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.components.button.ViewMode
import com.softistx.material.components.button.ViewToggle
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
private val chosen = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
private val notChosen = SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)

/**
 * SegmentedControl and ViewToggle are one connected button group: the ends are round, the seams
 * are squarer, the chosen segment is filled, and a Tab's press now reaches its style.
 */
@OptIn(ExperimentalTestApi::class)
class ConnectedGroupTest :
    FeatureSpec({
        feature("a segmented control's shape") {
            scenario("rounds the ends, squares the unchosen side of a seam and keeps the chosen one a pill") {
                val bounds = NodeBounds()
                renderProbe(
                    width = 400,
                    height = 200,
                    content = {
                        StxTheme(isDark = false) {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                SegmentedControl(
                                    options = listOf("XXXX", "XXXX"),
                                    selected = 0,
                                    onSelect = {},
                                    modifier = Modifier.padding(20.dp).probed(bounds),
                                )
                            }
                        }
                    },
                ) {
                    val grid = settle()
                    val r = bounds.rect
                    // The outer corners are a pill's: clear a few pixels in.
                    grid.isTransparent(bounds, Corner.TopLeft, inset = 2) shouldBe true
                    grid.isTransparent(bounds, Corner.TopRight, inset = 2) shouldBe true
                    // Six pixels from the seam, two down: the unchosen right segment's inner corner is a small
                    // radius, so it has ink there; the chosen left segment's is a pill's, so it has none.
                    val seam = r.left + r.width / 2
                    fun inkAt(x: Float) = grid.ink(Rect(x, r.top + 2, x + 1, r.top + 3))
                    inkAt(seam + 6) shouldBe 1
                    inkAt(seam - 7) shouldBe 0
                }
            }

            scenario("hugs its labels, or spans the width when the caller fills it") {
                fun width(modifier: Modifier): Float {
                    val bounds = NodeBounds()
                    return renderProbe(
                        width = 400,
                        height = 200,
                        content = {
                            StxTheme(isDark = false) {
                                SegmentedControl(
                                    options = listOf("Day", "Month"),
                                    selected = 0,
                                    onSelect = {},
                                    modifier = Modifier.padding(20.dp).then(modifier).probed(bounds),
                                )
                            }
                        },
                    ) {
                        settle()
                        bounds.rect.width
                    }
                }
                width(Modifier.fillMaxWidth()) shouldBe 360f
                (width(Modifier) < 360f) shouldBe true
            }

            scenario("fills the chosen segment and not the other") {
                fun colourOf(selected: Int): Pair<Color, Color> {
                    val bounds = NodeBounds()
                    return renderProbe(
                        width = 400,
                        height = 200,
                        content = {
                            StxTheme(isDark = false) {
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    SegmentedControl(
                                        options = listOf("AAAA", "BBBB"),
                                        selected = selected,
                                        onSelect = {},
                                        modifier = Modifier.padding(20.dp).probed(bounds),
                                    )
                                }
                            }
                        },
                    ) {
                        val grid = settle()
                        // 12 pixels in from each end is inside the container and clear of the label.
                        grid.pixelAt(bounds, Corner.TopLeft, inset = 12) to grid.pixelAt(bounds, Corner.TopRight, inset = 12)
                    }
                }
                val first = colourOf(0)
                val second = colourOf(1)
                (first.first != first.second) shouldBe true
                first.first shouldBe second.second
                first.second shouldBe second.first
            }
        }

        feature("a tab's press") {
            scenario("gives under the finger: the pressed tab's corner is no longer covered") {
                val bounds = NodeBounds()
                renderProbe(
                    width = 400,
                    height = 200,
                    content = {
                        StxTheme(isDark = false) {
                            Tabs(labels = listOf("Paid"), selected = 0, onSelect = {}, modifier = Modifier.probed(bounds))
                        }
                    },
                ) {
                    val rest = settle()
                    val restCorner = rest.pixelAt(bounds, Corner.TopLeft, inset = 0)
                    val restInside = rest.pixelAt(bounds, Corner.TopLeft, inset = 8)
                    val pressed = press(bounds)
                    // The ripple tints the tab, so the press reached it...
                    (pressed.pixelAt(bounds, Corner.TopLeft, inset = 8) != restInside) shouldBe true
                    // ...and the 0.97 scale pulled the tab's own corner inwards, off the outer pixel.
                    pressed.pixelAt(bounds, Corner.TopLeft, inset = 0) shouldBe restCorner
                }
            }
        }

        feature("a connected group's semantics") {
            scenario("each segment is a radio button, selected or not, and a 48 dp target") {
                runComposeUiTest {
                    var picked by mutableStateOf(1)
                    setContent {
                        StxTheme(isDark = false) {
                            SegmentedControl(
                                options = listOf("Day", "Week", "Month"),
                                selected = picked,
                                onSelect = { picked = it },
                                modifier = Modifier.testTag("group"),
                            )
                        }
                    }
                    onNodeWithText("Day").assert(radio).assert(notChosen)
                    onNodeWithTag("group").assertHeightIsAtLeast(48.dp)
                    onNodeWithText("Week").assert(radio).assert(chosen)
                    onNodeWithText("Month").performClick()
                    picked shouldBe 2
                    onNodeWithText("Month").assert(chosen)
                }
            }

            scenario("ViewToggle's two icons are radio buttons named List and Grid") {
                runComposeUiTest {
                    var mode by mutableStateOf(ViewMode.List)
                    setContent {
                        StxTheme(isDark = false) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { ViewToggle(value = mode, onChange = { mode = it }) }
                        }
                    }
                    onNodeWithContentDescription("List").assert(radio).assert(chosen)
                    onNodeWithContentDescription("Grid").assert(radio).assert(notChosen).performClick()
                    mode shouldBe ViewMode.Grid
                    onNodeWithContentDescription("Grid").assert(chosen)
                }
            }
        }
    })
