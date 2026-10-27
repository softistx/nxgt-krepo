package com.softistx.material.components.content

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.style.Style
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.softistx.material.components.form.DangerZone
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.hidden
import com.softistx.material.inkWith
import com.softistx.material.painted
import com.softistx.material.redPixels
import com.softistx.material.scanline
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

@OptIn(ExperimentalTestApi::class)
class ContentStyleTest :
    FeatureSpec({
        feature("press scale") {
            scenario("a clickable PinBar presses to 0.99 and takes the caller's style") {
                val line = scanline { PinBar("Ship it", it.fillMaxWidth(), onClick = {}, style = painted) }
                line.pressedBare(1) shouldBe true
                line.pressedFilled(3) shouldBe true
            }

            scenario("a PinBar without onClick does not scale") {
                scanline { PinBar("Ship it", it.fillMaxWidth(), style = painted) }.pressedFilled(1) shouldBe true
            }

            scenario("a MentionChip presses to 0.97, not 0.99") {
                val line = scanline { MentionChip("Ada", onClick = {}, modifier = it.width(400.dp), style = painted) }
                line.pressedBare(3) shouldBe true
                line.pressedFilled(8) shouldBe true
            }

            scenario("a FileChip with onClick presses to 0.97; without it, it is a receipt and does not scale") {
                val clickable = scanline { FileChip("a.pdf", it.width(400.dp), onClick = {}, style = painted) }
                clickable.pressedBare(3) shouldBe true
                clickable.pressedFilled(8) shouldBe true
                scanline { FileChip("a.pdf", it.width(400.dp), style = painted) }.pressedFilled(3) shouldBe true
            }

            scenario("a clickable LinkPreview presses to 0.99 through the card style it hands on") {
                val line = scanline { LinkPreview("Docs", "example.com", it.fillMaxWidth(), onClick = {}, style = painted) }
                line.pressedBare(1) shouldBe true
                line.pressedFilled(3) shouldBe true
            }
        }

        feature("the forwarded styles") {
            scenario("Rating hands its style to every star") {
                inkWith(Style) { Rating(3, {}, style = it) } shouldBeGreaterThan 300
                inkWith(hidden) { Rating(3, {}, style = it) } shouldBe 0
            }

            scenario("FilterBar hands its style to every chip") {
                inkWith(Style) { FilterBar(listOf("A", "B"), setOf("A"), {}, style = it) } shouldBeGreaterThan 300
                inkWith(hidden) { FilterBar(listOf("A", "B"), setOf("A"), {}, style = it) } shouldBe 0
            }

            scenario("ReactionBar hands its style to every chip") {
                val reactions = listOf(Reaction("+1", 2, selected = true), Reaction("ok", 1))
                inkWith(Style) { ReactionBar(reactions, style = it) } shouldBeGreaterThan 300
                inkWith(hidden) { ReactionBar(reactions, style = it) } shouldBe 0
            }

            scenario("CodeBlock hands its style to the copy button") {
                val code: @Composable (Style) -> Unit = { CodeBlock("val x = 1", style = it) }
                redPixels(code) shouldBeGreaterThan 500
            }

            scenario("ReplyPreview hands its style to its dismiss button") {
                val reply: @Composable (Style) -> Unit = { ReplyPreview("Ada", "See you", onDismiss = {}, style = it) }
                redPixels(reply) shouldBeGreaterThan 500
            }

            scenario("ExpandableText hands its style to the toggle, and leaves the text") {
                val long = "A long paragraph that wraps over many lines. ".repeat(12)
                val shown = inkWith(Style) { ExpandableText(long, Modifier.width(200.dp), collapsedLines = 1, style = it) }
                val dressed = inkWith(hidden) { ExpandableText(long, Modifier.width(200.dp), collapsedLines = 1, style = it) }
                shown shouldBeGreaterThan dressed + 50
                dressed shouldBeGreaterThan 100
            }
        }

        feature("semantics") {
            scenario("Card(onClick) is a button, and a card without onClick is not") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            Card(onClick = {}) { Text("Clickable") }
                        }
                    }
                    onNodeWithText("Clickable").assert(button)
                }
                runComposeUiTest {
                    setContent { StxTheme(isDark = false) { Card { Text("Still") } } }
                    onNodeWithText("Still").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
                }
            }

            scenario("SectionHeader, PricingCard and DangerZone announce their title as a heading, not the supporting text") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            SectionHeader("Recent orders", supporting = "Last 30 days")
                            PricingCard(name = "Pro", amount = "$12")
                            DangerZone(text = "No way back", title = "Delete account") {}
                        }
                    }
                    onNodeWithText("Recent orders").assert(heading)
                    onNodeWithText("Last 30 days").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
                    onNodeWithText("Pro").assert(heading)
                    onNodeWithText("Delete account").assert(heading)
                    onNodeWithText("No way back").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
                }
            }
        }

        feature("the signature") {
            scenario("signature guard: LinkPreview keeps onClick before style, and the trailing lambda is leading") {
                var clicked = false
                var led = false
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            LinkPreview("Docs", "example.com", Modifier, "About", { clicked = true }) {
                                led = true
                            }
                        }
                    }
                    onNodeWithText("Docs").performClick()
                }
                clicked shouldBe true
                led shouldBe true
            }
        }
    })
