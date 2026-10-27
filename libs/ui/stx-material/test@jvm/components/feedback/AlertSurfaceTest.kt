package com.softistx.material.components.feedback

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.RenderProbe
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.text.style
import com.softistx.material.foundation.theme.DefaultSeed
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.foundation.theme.stxColors
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private val colors = stxColors(DefaultSeed, isDark = false)
private val tones = listOf(Tone.Success, Tone.Info, Tone.Warning)

private fun <T> alertProbe(
    bounds: NodeBounds,
    notice: @Composable (Modifier) -> Unit,
    block: RenderProbe.() -> T,
): T =
    renderProbe(
        width = 420,
        height = 140,
        content = { StxTheme(isDark = false) { notice(Modifier.padding(10.dp).probed(bounds)) } },
        block = block,
    )

/** The alert and the bar share one surface: no outline, a 20 dp corner, the tone on a plate. */
@OptIn(ExperimentalTestApi::class)
class AlertSurfaceTest :
    FeatureSpec({
        feature("the outline") {
            scenario("no pixel of the frame is the tone's main colour, for an alert and for a bar") {
                tones.forEach { tone ->
                    val bounds = NodeBounds()
                    alertProbe(bounds, { Alert("Saved.", modifier = it, tone = tone, showIcon = false) }) {
                        settle().count(colors.tone(tone).main) shouldBe 0
                    }
                    alertProbe(bounds, { AnnouncementBar("Saved.", modifier = it, tone = tone, showIcon = false) }) {
                        settle().count(colors.tone(tone).main) shouldBe 0
                    }
                }
            }
        }

        feature("the shape") {
            scenario("the corner is 20 dp: clear 4 px in, painted 7 px in (12 dp would paint both)") {
                val bounds = NodeBounds()
                alertProbe(bounds, { Alert("Saved.", modifier = it) }) {
                    val frame = settle()
                    frame.isTransparent(bounds, Corner.TopLeft, inset = 4) shouldBe true
                    frame.isFilled(bounds, Corner.TopLeft, inset = 7) shouldBe true
                }
            }
        }

        feature("the surface") {
            scenario("a calm tone sits on surfaceContainerHigh, not on the tone's container") {
                val bounds = NodeBounds()
                alertProbe(bounds, { Alert("Saved.", modifier = it, tone = Tone.Success, showIcon = false) }) {
                    val frame = settle()
                    frame.pixelAt(bounds, Corner.BottomRight, inset = 12) shouldBe colors.scheme.surfaceContainerHigh
                    frame.count(colors.tone(Tone.Success).container) shouldBe 0
                }
            }

            scenario("an error keeps the error container") {
                val bounds = NodeBounds()
                alertProbe(bounds, { Alert("Failed.", modifier = it, tone = Tone.Error, showIcon = false) }) {
                    settle().pixelAt(bounds, Corner.BottomRight, inset = 12) shouldBe colors.tone(Tone.Error).container
                }
            }
        }

        feature("the leading plate") {
            scenario("a tone container spot appears with the plate and not without it") {
                tones.forEach { tone ->
                    val bounds = NodeBounds()
                    val container = colors.tone(tone).container
                    alertProbe(bounds, { Alert("Saved.", modifier = it, tone = tone) }) {
                        // 36 dp plate: a cookie, so well under a 36 x 36 square, well over a thin line.
                        settle().count(container) shouldBeGreaterThan 400
                        settle().count(container) shouldBeLessThan 1296
                    }
                    alertProbe(bounds, { Alert("Saved.", modifier = it, tone = tone, showIcon = false) }) {
                        settle().count(container) shouldBe 0
                    }
                }
            }

            scenario("the plate is a cookie, not a square: its corner shows the surface") {
                val bounds = NodeBounds()
                renderProbe(
                    width = 100,
                    height = 100,
                    content = { StxTheme(isDark = false) { TonePlate(Tone.Success, null, Modifier.padding(10.dp).probed(bounds)) } },
                ) {
                    val frame = settle()
                    frame.isTransparent(bounds, Corner.TopLeft) shouldBe true
                    frame.isTransparent(bounds, Corner.BottomRight) shouldBe true
                }
            }

            scenario("an icon of the caller's replaces the tone's") {
                fun iconInk(icon: ImageVector?): Int {
                    val bounds = NodeBounds()
                    var ink = 0
                    alertProbe(bounds, { Alert("Saved.", modifier = it, tone = Tone.Success, icon = icon) }) {
                        ink = settle().count(colors.tone(Tone.Success).onContainer)
                    }
                    return ink
                }
                iconInk(StxIcons.Star) shouldNotBe iconInk(null)
                iconInk(StxIcons.Check) shouldBe iconInk(null)
            }

            scenario("an icon with showIcon = false is refused, by message") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        runComposeUiTest { setContent { StxTheme { Alert("x", icon = StxIcons.Star, showIcon = false) } } }
                    }
                failure.message shouldBe "Alert: icon is set but showIcon = false; drop one of them"
                val bar =
                    shouldThrow<IllegalArgumentException> {
                        runComposeUiTest {
                            setContent { StxTheme { AnnouncementBar("x", icon = StxIcons.Star, showIcon = false) } }
                        }
                    }
                bar.message shouldBe "AnnouncementBar: icon is set but showIcon = false; drop one of them"
            }
        }

        feature("the title") {
            scenario("is the emphasised title weight, heavier than the plain one, and a heading") {
                var plain: FontWeight? = null
                var emphasised: FontWeight? = null
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            plain = TypographyVariant.TitleSmall.style(false).fontWeight
                            emphasised = TypographyVariant.TitleSmall.style(true).fontWeight
                            Alert("Body", title = "Heads up")
                        }
                    }
                    val node = onNodeWithText("Heads up")
                    node.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                    val results = mutableListOf<TextLayoutResult>()
                    node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                    results.single().layoutInput.style.fontWeight shouldBe emphasised
                }
                emphasised!!.weight shouldBeGreaterThan plain!!.weight
            }
        }
    })
