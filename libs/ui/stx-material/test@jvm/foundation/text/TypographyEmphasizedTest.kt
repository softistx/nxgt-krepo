package com.softistx.material.foundation.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.softistx.material.TestFonts
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.renderProbe
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * `emphasized` is the Material 3 `*Emphasized` type style, not a dimmer colour: proved by the style
 * it resolves to, and in pixels by the extra ink the heavier weight lays down at the same size.
 */
class TypographyEmphasizedTest :
    FeatureSpec({
        feature("Typography(emphasized)") {
            scenario("lays down more ink than the regular style at the same size") {
                val regular = ink(emphasized = false)
                val heavy = ink(emphasized = true)
                heavy shouldBeGreaterThan regular
            }

            scenario("makes each of the 15 M3 roles heavier, at M3's emphasised size") {
                val m3Roles = TypographyVariant.entries.take(M3_ROLES)
                val pairs = mutableListOf<Triple<TypographyVariant, TextStyle, TextStyle>>()
                renderProbe(width = 40, height = 40, content = {
                    StxTheme(strings = StxStrings.En, isDark = false) {
                        m3Roles.forEach { pairs += Triple(it, it.style(false), it.style(true)) }
                    }
                }) { settle() }
                pairs.size shouldBe M3_ROLES
                pairs.forEach { (variant, regular, heavy) ->
                    withClue(variant.name) {
                        (heavy.fontWeight!!.weight > regular.fontWeight!!.weight) shouldBe true
                        heavy.fontSize shouldBe regular.fontSize
                    }
                }
            }

            scenario("leaves the five extra variants as they were") {
                var same = false
                renderProbe(width = 40, height = 40, content = {
                    StxTheme(strings = StxStrings.En, isDark = false) {
                        same = TypographyVariant.Code.style(true) == TypographyVariant.Code.style(false) &&
                            TypographyVariant.Link.style(true) == TypographyVariant.Link.style(false)
                    }
                }) { settle() }
                same shouldBe true
            }

            scenario("keeps Metric at SemiBold, display-sized, never lighter than before") {
                var metric = TextStyle.Default
                var emphasisedDisplay = TextStyle.Default
                renderProbe(width = 40, height = 40, content = {
                    StxTheme(strings = StxStrings.En, isDark = false) {
                        metric = TypographyVariant.Metric.style()
                        emphasisedDisplay = MaterialTheme.typography.displaySmallEmphasized
                    }
                }) { settle() }
                metric.fontWeight shouldBe FontWeight.SemiBold
                metric.fontSize shouldBe emphasisedDisplay.fontSize
                (metric.fontWeight!!.weight >= FontWeight.SemiBold.weight) shouldBe true
            }

            scenario("keeps a theme's genuinely heavier emphasised style") {
                var got: FontWeight? = null
                val heavy = Typography().headlineMedium.copy(fontWeight = FontWeight.Bold)
                val bold = Typography(headlineMediumEmphasized = heavy)
                renderProbe(width = 40, height = 40, content = {
                    StxTheme(strings = StxStrings.En, isDark = false, typography = bold) {
                        got = TypographyVariant.HeadlineMedium.style(true).fontWeight
                    }
                }) { settle() }
                got shouldBe FontWeight.Bold
            }

            scenario("takes a role that is already Medium to SemiBold") {
                var got: FontWeight? = null
                val medium = Typography(titleSmall = TextStyle(fontWeight = FontWeight.Medium))
                renderProbe(width = 40, height = 40, content = {
                    StxTheme(strings = StxStrings.En, isDark = false, typography = medium) {
                        got = TypographyVariant.TitleSmall.style(true).fontWeight
                    }
                }) { settle() }
                got shouldBe FontWeight.SemiBold
            }

            scenario("keeps a heavier theme weight on Metric") {
                var got: FontWeight? = null
                val black = Typography(displaySmallEmphasized = TextStyle(fontWeight = FontWeight.Black))
                renderProbe(width = 40, height = 40, content = {
                    StxTheme(strings = StxStrings.En, isDark = false, typography = black) {
                        got = TypographyVariant.Metric.style().fontWeight
                    }
                }) { settle() }
                got shouldBe FontWeight.Black
            }
        }
    })

/** Ink laid down by one headline line, regular or emphasised. */
private fun ink(emphasized: Boolean): Int =
    renderProbe(width = 520, height = 90, content = {
        StxTheme(strings = StxStrings.En, isDark = false, typography = TestFonts.typography()) {
            Typography("Mathematics and Physics", TypographyVariant.HeadlineMedium, emphasized = emphasized)
        }
    }) { settle().ink() }

private const val M3_ROLES = 15
