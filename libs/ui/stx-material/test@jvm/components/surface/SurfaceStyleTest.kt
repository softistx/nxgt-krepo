package com.softistx.material.components.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.softistx.material.components.layout.LoadMoreButton
import com.softistx.material.components.layout.RefreshBox
import com.softistx.material.components.layout.ScrollToTop
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.hidden
import com.softistx.material.inkWith
import com.softistx.material.isPainted
import com.softistx.material.paintedWhenDisabled
import com.softistx.material.renderProbe
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

private val items = listOf(MenuItem("Open", {}))

@OptIn(ExperimentalTestApi::class)
class SurfaceStyleTest :
    FeatureSpec({
        feature("a style with no default look reaches the part") {
            scenario("each wrapper keeps its content by default and hides it under alpha 0") {
                val parts: Map<String, @Composable (Style) -> Unit> =
                    mapOf(
                        "ContextMenu" to { s -> ContextMenu(items, style = s) { Text("Anchor") } },
                        "Tooltip" to { s -> Tooltip("Hint", style = s) { Text("Anchor") } },
                        "HoverCard" to { s -> HoverCard("Title", "Body", style = s) { Text("Anchor") } },
                        "SwipeActions" to { s -> SwipeActions({}, background = {}, style = s) { Text("Anchor") } },
                        "RefreshBox" to { s -> RefreshBox(false, {}, style = s) { Text("Anchor") } },
                        "Drawer" to { s -> Drawer(false, {}, drawer = {}, style = s) { Text("Anchor") } },
                        "HelpTip" to { s -> HelpTip("Hint", style = s) },
                        "LoadMoreButton" to { s -> LoadMoreButton(true, false, {}, style = s) },
                        "ScrollToTop" to { s ->
                            Box(Modifier.fillMaxSize()) { ScrollToTop(rememberLazyListState(), after = 0, style = s) }
                        },
                    )
                parts.forEach { (name, part) ->
                    withClue(name) {
                        inkWith(Style, part) shouldBeGreaterThan 0
                        inkWith(hidden, part) shouldBe 0
                    }
                }
            }

            scenario("Drawer sees itself disabled while gestures are off") {
                fun painted(gestures: Boolean) =
                    renderProbe(width = 400, height = 300, content = {
                        StxTheme(isDark = false) {
                            Drawer(false, {}, gesturesEnabled = gestures, drawer = {}, style = paintedWhenDisabled) { Text("Anchor") }
                        }
                    }) { settle().count { it.isPainted() } }
                painted(gestures = true) shouldBe 0
                painted(gestures = false) shouldBeGreaterThan 1000
            }

            scenario("on a wrapper only static properties and disabled apply: disabled fires, hovered and pressed do not") {
                val states =
                    Style {
                        disabled { background(Color.Red) }
                        hovered { background(Color.Red) }
                        pressed { background(Color.Red) }
                    }

                fun reds(
                    gestures: Boolean,
                    pointer: Boolean,
                ) = renderProbe(width = 400, height = 300, content = {
                    StxTheme(isDark = false) {
                        Drawer(false, {}, gesturesEnabled = gestures, drawer = {}, style = states) { Text("Anchor") }
                    }
                }) { (if (pointer) press(Offset(200f, 150f)) else settle()).count { it.isPainted() } }
                reds(gestures = true, pointer = false) shouldBe 0
                reds(gestures = true, pointer = true) shouldBe 0
                reds(gestures = false, pointer = false) shouldBeGreaterThan 1000
            }
        }

        feature("SwipeActions customActions") {
            scenario("the caller's actions are named entries of the row, in order, with the caller's labels") {
                runComposeUiTest {
                    var archived = false
                    setContent {
                        StxTheme(isDark = false) {
                            SwipeActions(
                                onDismiss = {},
                                modifier = Modifier.testTag("row"),
                                background = {},
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction("Archive") { archived = true; true },
                                        CustomAccessibilityAction("Supprimer") { true },
                                    ),
                            ) { Text("Row") }
                        }
                    }
                    val actions = onNodeWithTag("row").fetchSemanticsNode().config[SemanticsActions.CustomActions]
                    actions.map { it.label } shouldBe listOf("Archive", "Supprimer")
                    actions.first().action() shouldBe true
                    archived shouldBe true
                }
            }

            scenario("without customActions the row announces no actions at all") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            SwipeActions({}, Modifier.testTag("row"), background = {}) { Text("Row") }
                        }
                    }
                    onNodeWithTag("row").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
                }
            }
        }

        feature("the signature") {
            scenario("signature guard: a trailing lambda is still the content, after style") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            ContextMenu(items) { Text("one") }
                            Tooltip("Hint") { Text("two") }
                            HoverCard("Title", "Body") { Text("three") }
                            SwipeActions({}, background = {}) { Text("four") }
                            Sheet(false, {}) { Text("five") }
                        }
                    }
                    listOf("one", "two", "three", "four").forEach { onNodeWithText(it).assertExists() }
                }
            }
        }
    })
