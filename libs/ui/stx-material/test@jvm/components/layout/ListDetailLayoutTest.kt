package com.softistx.material.components.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.NodeBounds
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

private class Panes {
    val list = NodeBounds()
    val detail = NodeBounds()
    var listRuns = 0
    var detailRuns = 0
}

private fun layoutAt(
    width: Int,
    selected: Boolean,
    panes: Panes,
    wideThreshold: Dp = ListDetailLayoutDefaults.WideThreshold,
    listWidth: Dp = ListDetailLayoutDefaults.ListWidth,
) = renderProbe(
    width = width,
    height = 300,
    content = {
        StxTheme(isDark = false) {
            ListDetailLayout(
                selected = selected,
                wideThreshold = wideThreshold,
                listWidth = listWidth,
                list = {
                    panes.listRuns++
                    Box(it.probed(panes.list))
                },
                detail = {
                    panes.detailRuns++
                    Box(it.probed(panes.detail))
                },
            )
        }
    },
) { settle() }

/** The layout follows the width it is offered: two panes from the threshold, one below it. */
@OptIn(ExperimentalTestApi::class)
class ListDetailLayoutTest :
    FeatureSpec({
        feature("the width it is offered") {
            scenario("below the threshold the list fills the width and the detail is not composed") {
                val panes = Panes()
                layoutAt(width = 719, selected = false, panes = panes)
                panes.list.rect.width shouldBe 719f
                panes.detailRuns shouldBe 0
            }

            scenario("below the threshold a selection swaps the list for the detail") {
                val panes = Panes()
                layoutAt(width = 600, selected = true, panes = panes)
                panes.detail.rect.width shouldBe 600f
                panes.list.rect.width shouldBe 0f
                panes.listRuns shouldBe 0
            }

            scenario("from the threshold the list is 380 dp and the detail takes the rest after the divider") {
                val panes = Panes()
                layoutAt(width = 720, selected = true, panes = panes)
                panes.list.rect.width shouldBe 380f
                panes.detail.rect.left shouldBe panes.list.rect.right + 1f
                panes.detail.rect.right shouldBe 720f
            }

            scenario("two panes with nothing selected show the placeholder and never compose the detail") {
                val panes = Panes()
                layoutAt(width = 900, selected = false, panes = panes)
                panes.list.rect.width shouldBe 380f
                panes.detailRuns shouldBe 0
            }

            scenario("the thresholds are parameters") {
                val panes = Panes()
                layoutAt(width = 500, selected = true, panes = panes, wideThreshold = 480.dp, listWidth = 200.dp)
                panes.list.rect.width shouldBe 200f
                panes.detail.rect.right shouldBe 500f
            }
        }

        feature("selection") {
            scenario("selection is plain state: the placeholder gives way to the detail and back") {
                runComposeUiTest {
                    var selected by mutableStateOf(false)
                    setContent {
                        StxTheme(isDark = false) {
                            ListDetailLayout(
                                selected = selected,
                                placeholder = { ListDetailLayoutDefaults.Placeholder("Select a row", it) },
                                list = { Typography(text = "the list", modifier = it) },
                                detail = { Typography(text = "the detail", modifier = it) },
                            )
                        }
                    }
                    onNodeWithText("the list").assertExists()
                    onNodeWithText("Select a row").assertExists()
                    onNodeWithText("Select an item").assertDoesNotExist()
                    onNodeWithText("the detail").assertDoesNotExist()
                    selected = true
                    onNodeWithText("the detail").assertExists()
                    onNodeWithText("Select a row").assertDoesNotExist()
                    selected = false
                    onNodeWithText("the detail").assertDoesNotExist()
                }
            }
        }

        feature("the default placeholder") {
            scenario("without a placeholder the second pane reads \"Select an item\"") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            Box(Modifier.width(900.dp)) {
                                ListDetailLayout(
                                    selected = false,
                                    list = { Typography(text = "the list", modifier = it) },
                                    detail = { Typography(text = "the detail", modifier = it) },
                                )
                            }
                        }
                    }
                    onNodeWithText("Select an item").assertExists()
                }
            }
        }

        feature("pane state") {
            scenario("the list's scroll position survives a fold and unfold") {
                runComposeUiTest {
                    var width by mutableStateOf(900.dp)
                    lateinit var listState: LazyListState
                    setContent {
                        StxTheme(isDark = false) {
                            Box(Modifier.width(width).height(200.dp)) {
                                ListDetailLayout(
                                    selected = false,
                                    list = { pane ->
                                        listState = rememberLazyListState()
                                        LazyColumn(pane, state = listState) {
                                            items((1..40).toList()) { Typography(text = "Row $it") }
                                        }
                                    },
                                    detail = { Typography(text = "the detail", modifier = it) },
                                )
                            }
                        }
                    }
                    runOnIdle { listState.requestScrollToItem(12) }
                    waitForIdle()
                    width = 500.dp
                    waitForIdle()
                    width = 900.dp
                    waitForIdle()
                    runOnIdle { listState.firstVisibleItemIndex shouldBe 12 }
                }
            }

            scenario("single pane: list, detail, list keeps the scroll position") {
                runComposeUiTest {
                    var selected by mutableStateOf(false)
                    lateinit var listState: LazyListState
                    setContent {
                        StxTheme(isDark = false) {
                            Box(Modifier.width(500.dp).height(200.dp)) {
                                ListDetailLayout(
                                    selected = selected,
                                    list = { pane ->
                                        listState = rememberLazyListState()
                                        LazyColumn(pane, state = listState) {
                                            items((1..40).toList()) { Typography(text = "Row $it") }
                                        }
                                    },
                                    detail = { Typography(text = "the detail", modifier = it) },
                                )
                            }
                        }
                    }
                    runOnIdle { listState.requestScrollToItem(12) }
                    waitForIdle()
                    selected = true
                    waitForIdle()
                    onNodeWithText("the detail").assertExists()
                    selected = false
                    waitForIdle()
                    runOnIdle { listState.firstVisibleItemIndex shouldBe 12 }
                }
            }
        }

        feature("semantics") {
            scenario("wide: the list precedes the detail in traversal; narrow with a selection: no list node") {
                runComposeUiTest {
                    var width by mutableStateOf(900.dp)
                    setContent {
                        StxTheme(isDark = false) {
                            Box(Modifier.width(width)) {
                                ListDetailLayout(
                                    selected = true,
                                    list = { Typography(text = "the list", modifier = it) },
                                    detail = { Typography(text = "the detail", modifier = it) },
                                )
                            }
                        }
                    }
                    val texts = onRoot().fetchSemanticsNode().children.flatMap { collectTexts(it) }
                    texts shouldBe listOf("the list", "the detail")
                    width = 500.dp
                    onNodeWithText("the list").assertDoesNotExist()
                    onNodeWithText("the detail").assertExists()
                }
            }
        }
    })

private fun collectTexts(node: SemanticsNode): List<String> {
    val own = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    return own + node.children.flatMap(::collectTexts)
}
