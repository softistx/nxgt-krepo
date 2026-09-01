package com.softistx.material.button

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import com.softistx.material.display.ActionChip
import com.softistx.material.display.Kbd
import com.softistx.material.display.StatusDot
import com.softistx.material.feedback.Progress
import com.softistx.material.feedback.ProgressKind
import com.softistx.material.form.QuantityField
import com.softistx.material.form.SliderField
import com.softistx.material.form.TagField
import com.softistx.material.icon.StxIcons
import com.softistx.material.media.AvatarGroup
import com.softistx.material.media.AvatarItem
import com.softistx.material.theme.StxTheme
import com.softistx.material.theme.Tone
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan

/**
 * The components this slice added, composed once, for the class of failure that compiles and then
 * throws at render time.
 */
class ToggleRenderTest :
    FeatureSpec({
        feature("every new toggle, token and chrome control") {
            scenario("renders without throwing, and paints something") {
                render { paint() } shouldBeGreaterThan 0
            }
        }
    })

private fun render(content: @Composable () -> Unit): Int {
    val scene = ImageComposeScene(width = 480, height = 1400)
    try {
        scene.setContent {
            StxTheme(isDark = false) {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color.White).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
        var time = 0L
        repeat(8) {
            time += 100_000_000L
            scene.render(time)
        }
        val map = scene.render(time).toComposeImageBitmap().toPixelMap()
        var ink = 0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                if (map[x, y] != Color.White) ink++
            }
        }
        return ink
    } finally {
        scene.close()
    }
}

@Composable
private fun paint() {
    ToggleButton(text = "Follow", checked = true, onCheckedChange = {})
    IconToggle(icon = StxIcons.Star, description = "Save", checked = true, onCheckedChange = {})
    CopyButton(text = "ord_9f3a")
    ActionChip(text = "Call", onClick = {})
    StatusDot(tone = Tone.Success)
    Kbd(keys = listOf("Ctrl", "K"))
    AvatarGroup(items = listOf(AvatarItem("Amara"), AvatarItem("Jonas"), AvatarItem("Priya")))
    TagField(tags = listOf("urgent"), onTagsChange = {})
    QuantityField(value = 2, onValueChange = {})
    SliderField(value = 20f..80f, onValueChange = {})
    Progress(kind = ProgressKind.Wavy)
}
