package com.softistx.material.components.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.RenderProbe
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
private val chosen = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
private val notChosen = SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
private val noRole = SemanticsMatcher.keyNotDefined(SemanticsProperties.Role)
private val noClick = SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick)

private fun probe(
    bounds: NodeBounds,
    tile: @Composable (Modifier) -> Unit,
    block: RenderProbe.() -> Unit,
) = renderProbe(
    width = 400,
    height = 200,
    content = { StxTheme(isDark = false) { tile(Modifier.padding(20.dp).probed(bounds)) } },
    block = block,
)

/**
 * A ListTile is M3's interactive `ListItem`: its shape morphs under the press instead of the row
 * scaling, a chosen row is filled, and a run of tiles can be one segmented block.
 */
@OptIn(ExperimentalTestApi::class)
class ListTileTest :
    FeatureSpec({
        feature("the press") {
            scenario("gives by morphing, not by scaling: a corner painted by the style stays put") {
                val bounds = NodeBounds()
                probe(
                    bounds,
                    { ListTile("Amara", onClick = {}, modifier = it, style = Style { background(Color.Red) }) },
                ) {
                    settle().pixelAt(bounds, Corner.TopLeft, inset = 0) shouldBe Color.Red
                    press(bounds).pixelAt(bounds, Corner.TopLeft, inset = 0) shouldBe Color.Red
                }
            }

            scenario("the shape morphs: a middle tile's squarer corner rounds under the press") {
                val bounds = NodeBounds()
                probe(bounds, { ListTile("Amara", onClick = {}, group = ListTileGroup(1, 3), modifier = it) }) {
                    // 2 px in, a 4 dp corner has ink and a 16 dp one does not.
                    settle().isTransparent(bounds, Corner.TopLeft, inset = 2) shouldBe false
                    press(bounds).isTransparent(bounds, Corner.TopLeft, inset = 2) shouldBe true
                }
            }
        }

        feature("the chosen row") {
            scenario("a selected tile is filled with a tone the unselected one does not have") {
                fun body(selected: Boolean?): Color {
                    val bounds = NodeBounds()
                    var out = Color.Unspecified
                    probe(bounds, { ListTile("Amara", onClick = {}, selected = selected, modifier = it) }) {
                        out = settle().pixelAt(bounds, Corner.TopLeft, inset = 12)
                    }
                    return out
                }
                (body(true) != body(false)) shouldBe true
                body(false) shouldBe body(null)
            }
        }

        feature("the caller's style") {
            scenario("a background painted by the style reaches the row, at rest and selected") {
                listOf<Boolean?>(null, true).forEach { selected ->
                    val bounds = NodeBounds()
                    probe(
                        bounds,
                        { ListTile("Amara", onClick = {}, selected = selected, modifier = it, style = Style { background(Color.Red) }) },
                    ) {
                        settle().pixelAt(bounds, Corner.TopLeft, inset = 0) shouldBe Color.Red
                    }
                }
            }

            scenario("it reaches a grouped tile too") {
                val bounds = NodeBounds()
                probe(
                    bounds,
                    { ListTile("Amara", onClick = {}, group = ListTileGroup(0, 3), modifier = it, style = Style { background(Color.Red) }) },
                ) {
                    settle().pixelAt(bounds, Corner.TopLeft, inset = 0) shouldBe Color.Red
                }
            }
        }

        feature("a segmented group") {
            // Corners are read 2 px in: a round corner (large radius) is clear there, a squarer one has ink.
            fun corners(
                index: Int,
                count: Int,
            ): List<Boolean> {
                val bounds = NodeBounds()
                var out = emptyList<Boolean>()
                probe(bounds, { ListTile("Amara", onClick = {}, group = ListTileGroup(index, count), modifier = it) }) {
                    val grid = settle()
                    out =
                        listOf(Corner.TopLeft, Corner.TopRight, Corner.BottomLeft, Corner.BottomRight).map {
                            grid.isTransparent(bounds, it, inset = 2)
                        }
                }
                return out
            }
            scenario("the first tile is round on top and squarer below") {
                corners(0, 3) shouldBe listOf(true, true, false, false)
            }
            scenario("a middle tile is squarer on all four corners") {
                corners(1, 3) shouldBe listOf(false, false, false, false)
            }
            scenario("the last tile is squarer on top and round below") {
                corners(2, 3) shouldBe listOf(false, false, true, true)
            }
            scenario("a group of one has a standalone tile's corners, all four") {
                fun shape(group: ListTileGroup?): List<Boolean> {
                    val bounds = NodeBounds()
                    var out = emptyList<Boolean>()
                    probe(bounds, { ListTile("Amara", onClick = {}, group = group, selected = true, modifier = it) }) {
                        val grid = settle()
                        out = listOf(Corner.TopLeft, Corner.TopRight, Corner.BottomLeft, Corner.BottomRight).map { grid.isTransparent(bounds, it, inset = 2) }
                    }
                    return out
                }
                shape(ListTileGroup(0, 1)) shouldBe shape(null)
            }
            scenario("selected or group without onClick is refused with a message") {
                listOf<@Composable () -> Unit>(
                    { ListTile("A", selected = true) },
                    { ListTile("A", group = ListTileGroup(0, 2)) },
                ).forEach { tile ->
                    val failure =
                        runCatching { runComposeUiTest { setContent { StxTheme(isDark = false) { tile() } } } }.exceptionOrNull()
                    generateSequence(failure) { it.cause }.any { it.message?.contains("need an onClick") == true } shouldBe true
                }
            }
            scenario("an index outside the count is refused") {
                runCatching { ListTileGroup(3, 3) }.isFailure shouldBe true
            }
        }

        feature("semantics") {
            scenario("an interactive tile is a button with a click, at least 48 dp tall") {
                runComposeUiTest {
                    var clicks by mutableStateOf(0)
                    setContent { StxTheme(isDark = false) { ListTile("Profile", onClick = { clicks++ }) } }
                    onNodeWithText("Profile").assert(button).assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
                    clicks shouldBe 1
                }
            }
            scenario("a tile without onClick has no click action and no role") {
                runComposeUiTest {
                    setContent { StxTheme(isDark = false) { ListTile("Profile") } }
                    onNodeWithText("Profile").assert(noClick).assert(noRole)
                }
            }
            scenario("selected = true/false is a radio button that is or is not selected") {
                runComposeUiTest {
                    var picked by mutableStateOf(1)
                    setContent {
                        StxTheme(isDark = false) {
                            Column {
                                ListTile("One", onClick = { picked = 0 }, selected = picked == 0)
                                ListTile("Two", onClick = { picked = 1 }, selected = picked == 1)
                            }
                        }
                    }
                    onNodeWithText("One").assert(radio).assert(notChosen)
                    onNodeWithText("Two").assert(radio).assert(chosen)
                    onNodeWithText("One").performClick()
                    picked shouldBe 0
                    onNodeWithText("One").assert(chosen)
                }
            }
            scenario("a grouped tile is a button, or a selected radio button when selected is given") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            Column {
                                ListTile("Plain", onClick = {}, group = ListTileGroup(0, 2))
                                ListTile("Chosen", onClick = {}, group = ListTileGroup(1, 2), selected = true)
                            }
                        }
                    }
                    onNodeWithText("Plain").assert(button)
                    onNodeWithText("Chosen").assert(radio).assert(chosen)
                }
            }
            scenario("enabled = false refuses a click and reads as not enabled, plain, selected and grouped") {
                runComposeUiTest {
                    var clicks by mutableStateOf(0)
                    setContent {
                        StxTheme(isDark = false) {
                            Column {
                                ListTile("Plain", onClick = { clicks++ }, enabled = false)
                                ListTile("Selected", onClick = { clicks++ }, enabled = false, selected = false)
                                ListTile("Grouped", onClick = { clicks++ }, enabled = false, group = ListTileGroup(0, 2))
                            }
                        }
                    }
                    listOf("Plain", "Selected", "Grouped").forEach { onNodeWithText(it).assertIsNotEnabled().performClick() }
                    clicks shouldBe 0
                }
            }
        }
    })
