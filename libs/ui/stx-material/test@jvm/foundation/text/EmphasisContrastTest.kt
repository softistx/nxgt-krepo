package com.softistx.material.foundation.text

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.softistx.material.contrast
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.theme.DefaultSeed
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.stxColorScheme
import com.softistx.material.renderProbe
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual

private const val BODY_AA = 4.5

/**
 * Medium (0.80) over Subtle (0.72), measured on the default theme as Medium minus Subtle contrast:
 * light 1.34 / 1.28 / 1.17 and dark 1.65 / 1.45 / 1.36 on surface / surfaceContainer / surfaceContainerHigh.
 * The smallest gap is 1.17; the floor is that minus a tolerance, so a Medium that drifts onto Subtle fails.
 */
private const val MIN_GAP = 1.0

private fun surfaces(scheme: ColorScheme): Map<String, Color> =
    mapOf(
        "surface" to scheme.surface,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
    )

/** The contrast of [emphasis] text on [surface], read off the rendered glyph's solid core, not computed from the alpha. */
private fun rendered(
    emphasis: Emphasis,
    scheme: ColorScheme,
    surface: Color,
    isDark: Boolean,
): Double =
    renderProbe(width = 160, height = 90, background = surface, content = {
        StxTheme(strings = StxStrings.En, isDark = isDark, colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                Typography("MMMM", emphasis = emphasis, variant = TypographyVariant.DisplayMedium)
            }
        }
    }) { contrast(settle().strongestInk(), surface) }

/** Subtle is the quietest step of body text, and the quietest step still has to be readable. */
class EmphasisContrastTest :
    FeatureSpec({
        feature("Emphasis.Subtle on the default theme") {
            listOf(false, true).forEach { isDark ->
                val scheme = stxColorScheme(DefaultSeed, isDark)
                val mode = if (isDark) "dark" else "light"
                surfaces(scheme).forEach { (name, surface) ->
                    scenario("holds 4.5:1 on $name in $mode, read off rendered pixels") {
                        withClue("$mode $name") {
                            rendered(Emphasis.Subtle, scheme, surface, isDark) shouldBeGreaterThanOrEqual BODY_AA
                        }
                    }

                    scenario("stays at least $MIN_GAP below Medium on $name in $mode") {
                        val subtle = rendered(Emphasis.Subtle, scheme, surface, isDark)
                        val medium = rendered(Emphasis.Medium, scheme, surface, isDark)
                        withClue("$mode $name subtle=$subtle medium=$medium") {
                            (medium - subtle) shouldBeGreaterThanOrEqual MIN_GAP
                        }
                    }
                }
            }
        }
    })
