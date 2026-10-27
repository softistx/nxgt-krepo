package com.softistx.material.components.media

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.Style
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.hidden
import com.softistx.material.inkWith
import com.softistx.material.painted
import com.softistx.material.scanline
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/** A 400 dp tile is taller than the 200 px scene: press and read 50 px under its top edge. */
private fun tileLine(onSelect: ((Int) -> Unit)?) =
    scanline(pressAt = { Offset(200f, it.rect.top + 50f) }, y = { it.rect.top + 50f }) {
        Gallery(listOf("a"), it.fillMaxWidth(), onSelect = onSelect, minSize = 400.dp, style = painted)
    }

@OptIn(ExperimentalTestApi::class)
class MediaStyleTest :
    FeatureSpec({
        feature("Gallery") {
            scenario("a selectable tile presses to 0.99 and takes the caller's style") {
                val line = tileLine(onSelect = {})
                line.rest.red shouldBe 1f
                line.pressedBare(1) shouldBe true
                line.pressedFilled(3) shouldBe true
            }

            scenario("without onSelect a tile does not scale, but the caller's style still applies") {
                val line = tileLine(onSelect = null)
                line.rest.red shouldBe 1f
                line.pressedFilled(1) shouldBe true
            }

            scenario("a tile without onSelect still takes the caller's alpha 0") {
                fun ink(style: Style) = inkWith(style) { Gallery(listOf("a"), onSelect = null, minSize = 200.dp, style = it) }
                ink(Style) shouldBeGreaterThan 100
                ink(hidden) shouldBe 0
            }

            scenario("a tile is a button and selects its own index") {
                runComposeUiTest {
                    var picked = -1
                    setContent {
                        StxTheme(isDark = false) { Gallery(listOf("a", "b"), onSelect = { picked = it }, minSize = 100.dp) }
                    }
                    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))[1].performClick()
                    picked shouldBe 1
                }
            }
        }

        feature("PersonCard") {
            scenario("a clickable card presses to 0.99 through the style it hands on") {
                val line = scanline { PersonCard("Ada", it.fillMaxWidth(), onClick = {}, style = painted) }
                line.pressedBare(1) shouldBe true
                line.pressedFilled(3) shouldBe true
            }

            scenario("signature guard: the trailing lambda is still the action, after style") {
                runComposeUiTest {
                    setContent { StxTheme(isDark = false) { PersonCard("Ada") { Text("Message") } } }
                    onNodeWithText("Message").assertExists()
                }
            }
        }
    })
