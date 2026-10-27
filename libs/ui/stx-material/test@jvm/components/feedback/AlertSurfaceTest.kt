package com.softistx.material.components.feedback

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import io.kotest.matchers.doubles.shouldBeGreaterThan as doubleGreater
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private val colors = stxColors(DefaultSeed, isDark = false)

/** WCAG contrast ratio of two opaque colours. */
private fun contrast(
    a: Color,
    b: Color,
): Double {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
}

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
            scenario("no pixel of the frame is the tone's main colour without the plate, for every tone") {
                Tone.entries.forEach { tone ->
                    val bounds = NodeBounds()
                    alertProbe(bounds, { Alert("Saved.", modifier = it, tone = tone, icon = null) }) {
                        settle().count(colors.tone(tone).main) shouldBe 0
                    }
                    alertProbe(bounds, { AnnouncementBar("Saved.", modifier = it, tone = tone, icon = null) }) {
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
                alertProbe(bounds, { Alert("Saved.", modifier = it, tone = Tone.Success, icon = null) }) {
                    val frame = settle()
                    frame.pixelAt(bounds, Corner.BottomRight, inset = 12) shouldBe colors.scheme.surfaceContainerHigh
                    frame.count(colors.tone(Tone.Success).container) shouldBe 0
                }
            }

            scenario("an error keeps the error container") {
                val bounds = NodeBounds()
                alertProbe(bounds, { Alert("Failed.", modifier = it, tone = Tone.Error, icon = null) }) {
                    settle().pixelAt(bounds, Corner.BottomRight, inset = 12) shouldBe colors.tone(Tone.Error).container
                }
            }
        }

        feature("the leading plate") {
            scenario("every tone paints its plate colour with the plate and not without it") {
                Tone.entries.forEach { tone ->
                    val bounds = NodeBounds()
                    val main = if (tone == Tone.Error) colors.tone(tone).onContainer else colors.tone(tone).main
                    alertProbe(bounds, { Alert("Saved.", modifier = it, tone = tone) }) {
                        // 36 dp plate: a cookie, so well under a 36 x 36 square, well over a thin line.
                        settle().count(main) shouldBeGreaterThan 400
                        settle().count(main) shouldBeLessThan 1296
                    }
                    alertProbe(bounds, { Alert("Saved.", modifier = it, tone = tone, icon = null) }) {
                        settle().count(main) shouldBe 0
                    }
                }
            }

            scenario("the plate colour read off the rendered pixels holds 3:1 on its own notice surface, for every tone") {
                Tone.entries.forEach { tone ->
                    val bounds = NodeBounds()
                    alertProbe(bounds, { Alert("Saved.", modifier = it, tone = tone) }) {
                        val frame = settle()
                        val role = colors.tone(tone)
                        val plate = frame.pixelAt(bounds, Corner.TopLeft, inset = 16 + 8)
                        val surface = frame.pixelAt(bounds, Corner.BottomRight, inset = 12)
                        plate shouldBe (if (tone == Tone.Error) role.onContainer else role.main)
                        surface shouldBe (if (tone == Tone.Error) role.container else colors.scheme.surfaceContainerHigh)
                        contrast(plate, surface) doubleGreater 3.0
                    }
                }
            }

            scenario("the plate is a cookie, not a square: its corner shows the surface") {
                val bounds = NodeBounds()
                renderProbe(
                    width = 100,
                    height = 100,
                    content = {
                        StxTheme(isDark = false) { TonePlate(Tone.Success, StxIcons.Check, Modifier.padding(10.dp).probed(bounds)) }
                    },
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
                        ink = settle().count(colors.tone(Tone.Success).onMain)
                    }
                    return ink
                }
                iconInk(StxIcons.Star) shouldNotBe iconInk(AlertDefaults.icon(Tone.Success))
                iconInk(StxIcons.Check) shouldBe iconInk(AlertDefaults.icon(Tone.Success))
            }

            scenario("a promo banner forwards its icon, and icon = null hides the plate") {
                val bounds = NodeBounds()
                val main = colors.tone(Tone.Success).main
                alertProbe(bounds, { PromoBanner("Sale", modifier = it) }) {
                    settle().count(main) shouldBeGreaterThan 400
                }
                alertProbe(bounds, { PromoBanner("Sale", modifier = it, icon = null) }) {
                    settle().count(main) shouldBe 0
                }
                fun ink(icon: ImageVector): Int {
                    var out = 0
                    alertProbe(bounds, { PromoBanner("Sale", modifier = it, icon = icon) }) {
                        out = settle().count(colors.tone(Tone.Success).onMain)
                    }
                    return out
                }
                ink(StxIcons.Star) shouldNotBe ink(StxIcons.Check)
            }

            scenario("a trailing action lambda still compiles after the icon parameter") {
                runComposeUiTest {
                    setContent { StxTheme { Alert("x") { } } }
                }
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
