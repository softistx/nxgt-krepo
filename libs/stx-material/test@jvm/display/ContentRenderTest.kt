package com.strange.material.display

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
import com.strange.material.button.ButtonVariant
import com.strange.material.button.ConfirmButton
import com.strange.material.datetime.RelativeTime
import com.strange.material.form.InlineEdit
import com.strange.material.form.PasswordMeter
import com.strange.material.theme.StrangeTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlin.time.Instant

class ContentRenderTest :
    FeatureSpec({
        feature("every new content control") {
            scenario("renders without throwing, and paints something") {
                render { paint() } shouldBeGreaterThan 0
            }
        }
    })

private fun render(content: @Composable () -> Unit): Int {
    val scene = ImageComposeScene(width = 480, height = 1400)
    try {
        scene.setContent {
            StrangeTheme(isDark = false) {
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
    val now = Instant.fromEpochSeconds(1_777_766_400)
    SuggestionChip(text = "Amara Diallo", onClick = {})
    FileChip(name = "invoice.pdf", sizeLabel = "240 KB", onRemove = {})
    SectionHeader(title = "Recent orders", supporting = "Last 7 days")
    CodeBlock(text = "ord_9f3a")
    ExpandableText(text = "A short line.")
    InlineEdit(value = "Quarterly report", onValueChange = {})
    ConfirmButton(text = "Delete", onConfirm = {}, variant = ButtonVariant.Filled)
    RelativeTime(at = now, now = now)
    PasswordMeter(value = "secret")
}
