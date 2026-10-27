package com.softistx.material.components.content

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.softistx.material.foundation.theme.DefaultSeed
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.foundation.theme.stxColors
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
            }

            scenario("paints a dot in the tone colour for a meaningful tone") {
                tonePixels(Tone.Success) shouldBeGreaterThan 10
                tonePixels(Tone.Error) shouldBeGreaterThan 10
            }
        }
    })

/** How many pixels of the frame are exactly the main colour of the stat's own [tone]. */
private fun tonePixels(tone: Tone): Int {
    val target = stxColors(DefaultSeed, isDark = false).tone(if (tone == Tone.Info) Tone.Success else tone).main
    val scene = ImageComposeScene(width = 300, height = 160)
    try {
        scene.setContent {
            StxTheme(isDark = false) {
                Stat(value = "12", label = "Students", delta = "+2 this term", tone = tone)
            }
        }
        var time = 0L
        repeat(8) {
            time += 100_000_000L
            scene.render(time)
        }
        val map = scene.render(time).toComposeImageBitmap().toPixelMap()
        var count = 0
        for (y in 0 until map.height) for (x in 0 until map.width) if (map[x, y] == target) count++
        return count
    } finally {
        scene.close()
    }
}
