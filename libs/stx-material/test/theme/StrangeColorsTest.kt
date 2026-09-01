package com.softistx.material.theme

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The semantic roles are *derived*, not listed, and that is the claim worth pinning: a hard-coded
 * green would pass a light-mode eyeball check and be unreadable in the dark theme. These scenarios
 * fail if anyone replaces the derivation with constants.
 */
class StrangeColorsTest :
    FeatureSpec({

        val seed = Color(0xFF5B5BD6)

        feature("a palette grown from a seed") {
            scenario("is the same palette every time for the same seed") {
                val first = strangeColors(seed, isDark = false)
                val second = strangeColors(seed, isDark = false)

                // Compared role by role rather than whole: M3's ColorScheme declares copy and
                // toString but neither equals nor hashCode, so two identical schemes are unequal.
                first.scheme.primary shouldBe second.scheme.primary
                first.scheme.surface shouldBe second.scheme.surface
                first.scheme.onSurfaceVariant shouldBe second.scheme.onSurfaceVariant
                first.success shouldBe second.success
                first.warningContainer shouldBe second.warningContainer
            }

            scenario("differs between light and dark") {
                val light = strangeColors(seed, isDark = false)
                val dark = strangeColors(seed, isDark = true)

                light.scheme.surface shouldNotBe dark.scheme.surface
                light.success shouldNotBe dark.success
            }

            scenario("answers a different seed with a different palette") {
                val other = strangeColors(Color(0xFFDD4444), isDark = false)

                strangeColors(seed, isDark = false).scheme.primary shouldNotBe other.scheme.primary
            }
        }

        feature("the three roles Material 3 does not have") {
            scenario("gives every tone a distinct main colour") {
                val colors = strangeColors(seed, isDark = false)
                val mains =
                    listOf(Tone.Success, Tone.Info, Tone.Warning, Tone.Error)
                        .map { colors.tone(it).main }

                mains.toSet().size shouldBe 4
            }

            scenario("pairs each with a foreground that is not the colour itself") {
                val colors = strangeColors(seed, isDark = false)

                listOf(Tone.Success, Tone.Info, Tone.Warning, Tone.Error).forEach { tone ->
                    val role = colors.tone(tone)
                    role.onMain shouldNotBe role.main
                    role.onContainer shouldNotBe role.container
                }
            }

            scenario("takes error from the M3 scheme rather than deriving a second one") {
                val colors = strangeColors(seed, isDark = false)

                colors.error shouldBe colors.scheme.error
                colors.onErrorContainer shouldBe colors.scheme.onErrorContainer
            }

            scenario("flips the semantic roles with the theme, not just the M3 ones") {
                val light = strangeColors(seed, isDark = false).tone(Tone.Warning)
                val dark = strangeColors(seed, isDark = true).tone(Tone.Warning)

                light.main shouldNotBe dark.main
                light.onMain shouldNotBe dark.onMain
            }
        }

        feature("adopting a scheme a caller already has") {
            scenario("keeps that scheme untouched and only adds the semantic roles") {
                val mine = strangeColors(Color(0xFF008080), isDark = false).scheme

                strangeColors(mine, isDark = false).scheme shouldBe mine
            }
        }
    })
