package com.softistx.material.foundation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe

/**
 * The palette is calm by default and readable always. These scenarios pin the three claims the
 * default makes: it is deterministic, every foreground sits on its background at WCAG AA, and
 * `info` is the scheme's own `secondary` rather than a fourth hue.
 */
class ThemeColorTest :
    FeatureSpec({

        val seeds = listOf(DefaultSeed, Color(0xFF00796B), Color(0xFFC2185B), Color(0xFFEF6C00), Color(0xFF475569))
        val styles = listOf(PaletteStyle.TonalSpot, PaletteStyle.Vibrant, PaletteStyle.Expressive)

        // M3's ColorScheme declares neither equals nor hashCode, so schemes are compared by role.
        fun roles(scheme: ColorScheme): List<Color> =
            with(scheme) {
                listOf(
                    primary, onPrimary, primaryContainer, onPrimaryContainer, secondary, onSecondary,
                    secondaryContainer, onSecondaryContainer, tertiary, onTertiary, tertiaryContainer,
                    onTertiaryContainer, error, onError, errorContainer, onErrorContainer, background,
                    onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, inverseSurface,
                    inverseOnSurface, outline, outlineVariant, surfaceContainerLowest, surfaceContainerLow,
                    surfaceContainer, surfaceContainerHigh, surfaceContainerHighest,
                )
            }

        fun contrast(
            a: Color,
            b: Color,
        ): Double {
            val hi = maxOf(a.luminance(), b.luminance()).toDouble()
            val lo = minOf(a.luminance(), b.luminance()).toDouble()
            return (hi + 0.05) / (lo + 0.05)
        }

        fun pairs(scheme: ColorScheme): Map<String, Pair<Color, Color>> =
            with(scheme) {
                mapOf(
                    "primary" to (onPrimary to primary),
                    "primaryContainer" to (onPrimaryContainer to primaryContainer),
                    "secondary" to (onSecondary to secondary),
                    "secondaryContainer" to (onSecondaryContainer to secondaryContainer),
                    "tertiary" to (onTertiary to tertiary),
                    "tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
                    "error" to (onError to error),
                    "errorContainer" to (onErrorContainer to errorContainer),
                    "background" to (onBackground to background),
                    "surface" to (onSurface to surface),
                    "surfaceVariant" to (onSurfaceVariant to surfaceVariant),
                    "inverseSurface" to (inverseOnSurface to inverseSurface),
                ) +
                    listOf(
                        "Lowest" to surfaceContainerLowest,
                        "Low" to surfaceContainerLow,
                        "" to surfaceContainer,
                        "High" to surfaceContainerHigh,
                        "Highest" to surfaceContainerHighest,
                    ).flatMap { (name, container) ->
                        listOf(
                            "onSurface on surfaceContainer$name" to (onSurface to container),
                            "onSurfaceVariant on surfaceContainer$name" to (onSurfaceVariant to container),
                        )
                    }
            }

        feature("the same seed") {
            scenario("gives the same scheme, role by role, for every palette style") {
                for (style in styles) {
                    for (dark in listOf(false, true)) {
                        val first = stxColorScheme(DefaultSeed, dark, style)
                        val second = stxColorScheme(DefaultSeed, dark, style)

                        roles(first) shouldBe roles(second)
                    }
                }
            }

            scenario("defaults to TonalSpot on the 2025 spec") {
                val explicit = stxColorScheme(DefaultSeed, false, PaletteStyle.TonalSpot, ColorSpec.SpecVersion.SPEC_2025)

                roles(stxColorScheme(DefaultSeed, false)) shouldBe roles(explicit)
            }

            scenario("changes with the palette style") {
                val calm = stxColorScheme(DefaultSeed, false, PaletteStyle.TonalSpot)
                val loud = stxColorScheme(DefaultSeed, false, PaletteStyle.Vibrant)

                (roles(calm) == roles(loud)) shouldBe false
            }
        }

        feature("every foreground on its background") {
            scenario("meets AA (4.5:1) in light and dark, for each palette style and seed") {
                for (style in styles) {
                    for (seed in seeds) {
                        for (dark in listOf(false, true)) {
                            pairs(stxColorScheme(seed, dark, style)).forEach { (name, pair) ->
                                withClue("$name, $style, dark=$dark, seed=$seed") {
                                    contrast(pair.first, pair.second) shouldBeGreaterThanOrEqualTo 4.5
                                }
                            }
                        }
                    }
                }
            }

            scenario("holds for the semantic tones too") {
                for (style in styles) {
                    for (dark in listOf(false, true)) {
                        val colors = stxColors(DefaultSeed, dark, style)
                        Tone.entries.forEach { tone ->
                            val role = colors.tone(tone)
                            withClue("$tone, $style, dark=$dark") {
                                contrast(role.onMain, role.main) shouldBeGreaterThanOrEqualTo 4.5
                                contrast(role.onContainer, role.container) shouldBeGreaterThanOrEqualTo 4.5
                            }
                        }
                    }
                }
            }
        }

        feature("the info tone") {
            scenario("is the scheme's secondary roles by default") {
                for (dark in listOf(false, true)) {
                    val colors = stxColors(DefaultSeed, dark)
                    val info = colors.tone(Tone.Info)

                    info.main shouldBe colors.scheme.secondary
                    info.onMain shouldBe colors.scheme.onSecondary
                    info.container shouldBe colors.scheme.secondaryContainer
                    info.onContainer shouldBe colors.scheme.onSecondaryContainer
                }
            }

            scenario("grows its own role when a colour is named") {
                val scheme = stxColorScheme(DefaultSeed, false)
                val blue = stxColors(scheme, isDark = false, info = Color(0xFF2563EB))

                (blue.info == scheme.secondary) shouldBe false
            }
        }
    })
