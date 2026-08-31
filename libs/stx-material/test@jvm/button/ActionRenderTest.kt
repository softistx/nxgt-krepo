package com.strange.material.button

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
import com.strange.material.display.FilterBar
import com.strange.material.display.LabeledDivider
import com.strange.material.display.Rating
import com.strange.material.display.Stat
import com.strange.material.feedback.LoadingMark
import com.strange.material.form.UploadField
import com.strange.material.icon.StrangeIcons
import com.strange.material.surface.MenuItem
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan

/**
 * The components this slice added, composed once, for the class of failure that compiles and then
 * throws at render time.
 */
class ActionRenderTest :
    FeatureSpec({
        feature("every new action and chrome control") {
            scenario("renders without throwing, and paints something") {
                render { paint() } shouldBeGreaterThan 0
            }
        }
    })

private fun render(content: @Composable () -> Unit): Int {
    val scene = ImageComposeScene(width = 480, height = 1100)
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
    Fab(icon = StrangeIcons.Add, description = "New", onClick = {})
    Fab(
        icon = StrangeIcons.Add,
        description = "New order",
        onClick = {},
        text = "New order",
        expanded = true,
    )
    FabMenu(
        expanded = false,
        onExpandedChange = {},
        actions = listOf(FabAction("Edit", StrangeIcons.Edit, onClick = {})),
    )
    SplitButton(
        text = "Save",
        onClick = {},
        overflow = listOf(MenuItem("Save as draft", onClick = {})),
    )
    Stat(value = "128", label = "Orders", delta = "+12%", tone = Tone.Success)
    FilterBar(options = listOf("Paid", "Pending"), selected = setOf("Paid"), onChange = {})
    Rating(value = 3, onChange = {})
    LabeledDivider(label = "or")
    UploadField(onClick = {})
    LoadingMark()
}
