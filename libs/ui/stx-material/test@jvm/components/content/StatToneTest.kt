package com.softistx.material.components.content

import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.theme.DefaultSeed
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.foundation.theme.stxColors
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * The accent budget on a [Stat]: a delta is a line of text, and only a tone that carries meaning
 * paints a coloured dot. Proved in pixels, by counting the tone's exact colour in the frame.
 */
class StatToneTest :
    FeatureSpec({
        feature("the delta of a stat") {
            scenario("paints no tone colour for the neutral Info tone") {
                tonePixels(Tone.Info) shouldBe 0
                tonePixels(Tone.Info, secondary = true) shouldBe 0
            }

            scenario("paints a dot in the tone colour for a meaningful tone") {
                tonePixels(Tone.Success) shouldBeGreaterThan 10
                tonePixels(Tone.Error) shouldBeGreaterThan 10
            }
        }
    })

/** How many pixels of the frame are exactly the main colour of the stat's own [tone]. */
private fun tonePixels(
    tone: Tone,
    secondary: Boolean = false,
): Int {
    val colors = stxColors(DefaultSeed, isDark = false)
    val target =
        when {
            secondary -> colors.scheme.secondary
            tone == Tone.Info -> colors.tone(Tone.Success).main
            else -> colors.tone(tone).main
        }
    return renderProbe(width = 300, height = 160, content = {
        StxTheme(strings = StxStrings.En, isDark = false) {
            Stat(value = "12", label = "Students", delta = "+2 this term", tone = tone)
        }
    }) { settle().count(target) }
}
