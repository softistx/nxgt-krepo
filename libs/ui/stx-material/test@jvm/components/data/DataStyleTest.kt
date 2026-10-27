package com.softistx.material.components.data

import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.isBare
import com.softistx.material.isPainted
import com.softistx.material.painted
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

private val oneColumn = listOf(TableColumn<String>("Name") { it })

/** The pixels of one scanline: the left edge at rest, and under the pointer at 1, 3 and 8 px in. */
private class Scanline(
    val rest: Color,
    val pressed: Map<Int, Color>,
)

/**
 * The wide table at 400 dp (one column, so a header or a row spans the whole width), pressed at
 * [pressAt] and read along the scanline [y] of the table's bounds. At 1 px per dp a scale s moves
 * the edge in by 400 x (1 - s) / 2: 2 px for 0.99, 6 px for 0.97, so the pixel 3 px in is painted
 * for 0.99 and bare for 0.97. The ripple darkens the red, which is why only the green channel is read.
 */
private fun scanline(
    pressAt: (NodeBounds) -> Offset,
    y: (NodeBounds) -> Float,
    table: @Composable (Modifier) -> Unit,
): Scanline {
    val bounds = NodeBounds()
    return renderProbe(width = 400, height = 200, content = {
        StxTheme(isDark = false) { table(Modifier.probed(bounds)) }
    }) {
        val first = settle()
        val at = { x: Int -> NodeBounds().also { it.rect = Rect(x.toFloat(), y(bounds), x + 1f, y(bounds) + 1f) } }
        val down = press(pressAt(bounds))
        Scanline(first.pixelAt(at(0), Corner.TopLeft), listOf(1, 3, 8).associateWith { down.pixelAt(at(it), Corner.TopLeft) })
    }
}

@OptIn(ExperimentalTestApi::class)
class DataStyleTest :
    FeatureSpec({
        feature("DataTable rows") {
            scenario("a clickable row of the wide table takes the caller's style and presses to 0.99") {
                val line =
                    scanline(pressAt = { Offset(200f, it.rect.bottom - 12f) }, y = { it.rect.bottom - 4f }) { m ->
                        DataTable(oneColumn, listOf("Ada"), m, collapseBelow = 100.dp, style = painted, onRowClick = {})
                    }
                line.rest.isPainted() shouldBe true
                line.pressed.getValue(1).isBare() shouldBe true
                line.pressed.getValue(3).isPainted() shouldBe true
            }

            scenario("a row without onRowClick takes no style: nothing is painted") {
                renderProbe(width = 400, height = 200, content = {
                    StxTheme(isDark = false) {
                        DataTable(oneColumn, listOf("Ada"), collapseBelow = 100.dp, style = painted)
                    }
                }) { settle().count { it.isPainted() } shouldBe 0 }
            }

            scenario("a collapsed card gets the caller's style too: alpha 0 hides it, the default shows it") {
                fun ink(style: Style) =
                    renderProbe(width = 400, height = 300, content = {
                        StxTheme(isDark = false) {
                            DataTable(oneColumn, listOf("Ada"), collapseBelow = 10_000.dp, style = style)
                        }
                    }) { settle().ink() }
                ink(Style) shouldBeGreaterThan 500
                ink(Style { alpha(0f) }) shouldBe 0
            }
        }

        feature("DataTable sortable headers") {
            scenario("a header takes the header style and presses to 0.97") {
                val line =
                    scanline(pressAt = { Offset(200f, it.rect.top + 8f) }, y = { it.rect.top + 6f }) { m ->
                        DataTable(oneColumn, listOf("Ada"), m, collapseBelow = 100.dp, onSortChange = {}, headerStyle = painted)
                    }
                line.rest.isPainted() shouldBe true
                line.pressed.getValue(3).isBare() shouldBe true
                line.pressed.getValue(8).isPainted() shouldBe true
            }

            scenario("a click sorts ascending first, then flips, and the current header announces its direction") {
                runComposeUiTest {
                    val asked = mutableListOf<TableSort>()
                    var sort by mutableStateOf<TableSort?>(null)
                    setContent {
                        StxTheme(isDark = false) {
                            DataTable(
                                columns = listOf(TableColumn<String>("Name") { it }, TableColumn("Score") { it }),
                                rows = listOf("Ada"),
                                collapseBelow = 100.dp,
                                sort = sort,
                                onSortChange = {
                                    asked += it
                                    sort = it
                                },
                            )
                        }
                    }
                    val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
                    onNodeWithText("Name").assert(button).assertHasClickAction().performClick()
                    onNodeWithText("Name").performClick()
                    onNodeWithText("Score").performClick()
                    asked shouldBe
                        listOf(
                            TableSort(0, SortDirection.Asc),
                            TableSort(0, SortDirection.Desc),
                            TableSort(1, SortDirection.Asc),
                        )
                }
            }

            scenario("the sorted header says Ascending or Descending; the others say nothing") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            DataTable(
                                columns = listOf(TableColumn<String>("Name") { it }, TableColumn("Score") { it }),
                                rows = listOf("Ada"),
                                collapseBelow = 100.dp,
                                sort = TableSort(1, SortDirection.Desc),
                                onSortChange = {},
                            )
                        }
                    }
                    onNodeWithText("Score").assert(
                        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Descending"),
                    )
                    onNodeWithText("Name").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
                }
            }

            scenario("sortable = { it == 0 }: column 0 is a button, column 1 is plain text") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            DataTable(
                                columns = listOf(TableColumn<String>("Name") { it }, TableColumn("Score") { it }),
                                rows = listOf("Ada"),
                                collapseBelow = 100.dp,
                                onSortChange = {},
                                sortable = { it == 0 },
                            )
                        }
                    }
                    onNodeWithText("Name").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                    onNodeWithText("Score").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
                    onNodeWithText("Score").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
                }
            }

            scenario("a display-only sort (no onSortChange) still announces its direction, without a click") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            DataTable(oneColumn, listOf("Ada"), collapseBelow = 100.dp, sort = TableSort(0, SortDirection.Asc))
                        }
                    }
                    onNodeWithText("Name").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ascending"))
                    onNodeWithText("Name").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
                }
            }

            scenario("without onSortChange a header is plain text, not a button") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) { DataTable(oneColumn, listOf("Ada"), collapseBelow = 100.dp) }
                    }
                    onNodeWithText("Name").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
                }
            }

            scenario("nextSort starts a new column ascending and flips the current one") {
                nextSort(null, 2) shouldBe TableSort(2, SortDirection.Asc)
                nextSort(TableSort(2, SortDirection.Asc), 1) shouldBe TableSort(1, SortDirection.Asc)
                nextSort(TableSort(2, SortDirection.Asc), 2) shouldBe TableSort(2, SortDirection.Desc)
            }
        }

        feature("the forwarded styles") {
            scenario("Pagination hands its style to both buttons: the left edge and the right edge of the row are Red") {
                val bounds = NodeBounds()
                renderProbe(width = 400, height = 200, content = {
                    StxTheme(isDark = false) {
                        Pagination(true, true, {}, {}, Modifier.probed(bounds), style = painted)
                    }
                }) {
                    val grid = settle()
                    grid.pixelAt(bounds, Corner.TopLeft) shouldBe Color.Red
                    grid.pixelAt(bounds, Corner.TopRight) shouldBe Color.Red
                }
            }

            scenario("SortControl hands its style to the field: the corner is Red, not faded, when disabled") {
                val bounds = NodeBounds()
                renderProbe(width = 400, height = 200, content = {
                    StxTheme(isDark = false) {
                        SortControl(
                            options = listOf("A"),
                            selected = "A",
                            direction = SortDirection.Asc,
                            onSelectedChange = {},
                            onDirectionChange = {},
                            modifier = Modifier.probed(bounds),
                            enabled = false,
                            style = painted,
                        )
                    }
                }) { settle().pixelAt(bounds, Corner.TopLeft) shouldBe Color.Red }
            }
        }

        feature("CommandPalette") {
            scenario("the caller's style dresses the list of commands, and nothing is painted without it") {
                fun paintedCount(style: Style) =
                    renderProbe(width = 600, height = 600, content = {
                        StxTheme(isDark = false) {
                            CommandPalette(true, "", {}, listOf(CommandItem("Open", {})), {}, style = style)
                        }
                    }) { settle().count { it.isPainted() } }
                paintedCount(Style) shouldBe 0
                paintedCount(painted) shouldBeGreaterThan 1000
            }
        }

        feature("the title") {
            scenario("EntityHeader announces its title as a heading") {
                runComposeUiTest {
                    setContent { StxTheme(isDark = false) { EntityHeader(title = "Amara Diallo", supporting = "Customer") } }
                    onNodeWithText("Amara Diallo").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                    onNodeWithText("Customer").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
                }
            }
        }

        feature("the signature") {
            scenario("signature guard: a trailing lambda is still the row click, after style") {
                runComposeUiTest {
                    var clicked: String? = null
                    setContent {
                        StxTheme(isDark = false) {
                            DataTable(oneColumn, listOf("Ada"), collapseBelow = 100.dp) { clicked = it }
                        }
                    }
                    onNodeWithText("Ada").performClick()
                    clicked shouldBe "Ada"
                }
            }
        }
    })
