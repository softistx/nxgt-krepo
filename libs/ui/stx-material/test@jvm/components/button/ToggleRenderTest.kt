package com.softistx.material.components.button

import androidx.compose.runtime.Composable
import com.softistx.material.components.content.ActionChip
import com.softistx.material.components.content.Kbd
import com.softistx.material.components.feedback.Progress
import com.softistx.material.components.feedback.ProgressKind
import com.softistx.material.components.feedback.StatusDot
import com.softistx.material.components.form.QuantityField
import com.softistx.material.components.form.SliderField
import com.softistx.material.components.form.TagField
import com.softistx.material.components.media.AvatarGroup
import com.softistx.material.components.media.AvatarItem
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.renderPage
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

private fun render(content: @Composable () -> Unit): Int = renderPage(480, 1400, content)

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
