package com.softistx.material.components.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.motion.StxMotion
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.DefaultSeed
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.foundation.theme.stxColors
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe

private val colors = stxColors(DefaultSeed, isDark = false)

private fun <T> loadingProbe(
    reducedMotion: Boolean,
    bounds: NodeBounds,
    count: Int = 4,
    block: com.softistx.material.RenderProbe.() -> T,
): T =
    renderProbe(
        width = 420,
        height = 520,
        content = {
            val motion = if (reducedMotion) StxMotion(enabled = false) else StxTheme.motion
            StxTheme(
                strings = StxStrings.En,
                isDark = false,
                motion = motion,
            ) { SkeletonCards(Modifier.padding(10.dp).probed(bounds), count) }
        },
        block = block,
    )

/** The loading face is cards that arrive in turn, the error face is the error alert, and neither moves under reduced motion. */
@OptIn(ExperimentalTestApi::class)
class StateHostTest :
    FeatureSpec({
        feature("the loading face") {
            scenario("each placeholder is a card: its corner is clear and its inside is painted") {
                val bounds = NodeBounds()
                loadingProbe(reducedMotion = true, bounds = bounds, count = 1) {
                    val grid = settle()
                    grid.isTransparent(bounds, Corner.TopLeft) shouldBe true
                    grid.isFilled(bounds, Corner.TopLeft, inset = 14) shouldBe true
                }
            }

            scenario("the cards arrive in sequence: less is drawn on the first frame than once settled") {
                loadingProbe(reducedMotion = false, bounds = NodeBounds()) {
                    val first = firstFrame().ink()
                    val settled = settle().ink()
                    first shouldBeLessThan settled
                }
            }

            scenario("with reduced motion everything is drawn on the first frame") {
                loadingProbe(reducedMotion = true, bounds = NodeBounds()) {
                    val first = firstFrame().ink()
                    first shouldBeGreaterThan 0
                    first shouldBe settle().ink()
                }
            }
        }

        feature("the error face") {
            scenario("it is the error alert: the error container, not the page") {
                val bounds = NodeBounds()
                renderProbe(
                    width = 420,
                    height = 200,
                    content = {
                        StxTheme(strings = StxStrings.En, isDark = false) {
                            StateHost<String>(UiState.Error("Down."), onRetry = {}, modifier = Modifier.padding(10.dp).probed(bounds)) {}
                        }
                    },
                ) {
                    settle().pixelAt(bounds, Corner.BottomRight, inset = 12) shouldBe colors.tone(Tone.Error).container
                }
            }
        }

        feature("semantics") {
            scenario("Retry is a button at least 48 dp tall that calls onRetry") {
                runComposeUiTest {
                    var retries by mutableStateOf(0)
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false) {
                            StateHost<String>(UiState.Error("Down."), onRetry = { retries++ }) {}
                        }
                    }
                    onNodeWithText("Retry")
                        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                        .assertHeightIsAtLeast(48.dp)
                        .performClick()
                    retries shouldBe 1
                    onNodeWithText("Could not load").assertExists()
                    onNodeWithText("Down.").assertExists()
                }
            }

            scenario("the loading face is described for a screen reader, and its words can be translated") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false) {
                            StateHost<String>(UiState.Loading, onRetry = {}, loadingLabel = "Chargement") {}
                        }
                    }
                    onNodeWithContentDescription("Chargement").assertExists()
                    onNodeWithContentDescription("Loading").assertDoesNotExist()
                }
            }

            scenario("the strings are parameters") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false) {
                            StateHost<String>(UiState.Error("Hors ligne."), onRetry = {}, errorTitle = "Échec", retryLabel = "Réessayer") {}
                        }
                    }
                    onNodeWithText("Échec").assertExists()
                    onNodeWithText("Réessayer").assertExists()
                }
            }
        }

        feature("the faces") {
            scenario("the data face is laid out within the modifier's bounds") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false) {
                            StateHost(UiState.Data("ok"), onRetry = {}, modifier = Modifier.size(120.dp, 60.dp)) {
                                Box(Modifier.fillMaxSize().testTag("data"))
                            }
                        }
                    }
                    onNodeWithTag("data").assertWidthIsEqualTo(120.dp).assertHeightIsEqualTo(60.dp)
                }
            }

            scenario("a custom loading slot replaces the default skeletons") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false, motion = StxMotion(enabled = false)) {
                            StateHost<String>(UiState.Loading, onRetry = {}, loading = { Typography(text = "my skeleton") }) {}
                        }
                    }
                    onNodeWithText("my skeleton").assertExists()
                    (onNodeWithContentDescription("Loading").getUnclippedBoundsInRoot().height < 60.dp) shouldBe true
                }
            }

            scenario("the loading and error faces are polite live regions") {
                runComposeUiTest {
                    var state: UiState<String> by mutableStateOf(UiState.Loading)
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false, motion = StxMotion(enabled = false)) {
                            StateHost(state, onRetry = {}) {}
                        }
                    }
                    val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
                    onNodeWithContentDescription("Loading").assert(polite)
                    state = UiState.Error("Down.")
                    waitForIdle()
                    onNode(polite and hasAnyDescendant(hasText("Could not load")), useUnmergedTree = true).assertExists()
                    onNodeWithContentDescription("Loading").assertDoesNotExist()
                }
            }
        }

        feature("switching state") {
            scenario("Loading, then Data, then Error, then Retry back to Loading") {
                runComposeUiTest {
                    var state: UiState<String> by mutableStateOf(UiState.Loading)
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false, motion = StxMotion(enabled = false)) {
                            StateHost(state, onRetry = { state = UiState.Loading }) { Typography(text = "Loaded $it") }
                        }
                    }
                    onNodeWithContentDescription("Loading").assertExists()
                    onNodeWithText("Loaded ok").assertDoesNotExist()

                    state = UiState.Data("ok")
                    waitForIdle()
                    onNodeWithText("Loaded ok").assertExists()
                    onNodeWithContentDescription("Loading").assertDoesNotExist()

                    state = UiState.Error("Down.")
                    waitForIdle()
                    onNodeWithText("Retry").assertExists()
                    onNodeWithText("Loaded ok").assertDoesNotExist()

                    onNodeWithText("Retry").performClick()
                    waitForIdle()
                    onNodeWithContentDescription("Loading").assertExists()
                }
            }
        }
    })
