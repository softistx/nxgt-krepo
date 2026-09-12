package com.softistx.material.button

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import com.softistx.material.feedback.LabeledProgress
import com.softistx.material.form.CheckState
import com.softistx.material.form.CopyField
import com.softistx.material.form.TriStateCheckbox
import com.softistx.material.icon.StxIcons
import com.softistx.material.layout.SelectionBar
import com.softistx.material.surface.Disclosure
import com.softistx.material.surface.MenuItem
import com.softistx.material.text.Typography
import com.softistx.material.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan

class BusyRenderTest :
    FeatureSpec({
        feature("every new busy and overflow control") {
            scenario("renders without throwing, and paints something") {
                render { paint() } shouldBeGreaterThan 0
            }
        }
    })

private fun render(content: @Composable () -> Unit): Int {
    val scene = ImageComposeScene(width = 480, height = 1200)
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
    BusyButton(text = "Save", onClick = {}, busy = true)
    MoreMenu(items = listOf(MenuItem("Edit", onClick = {})))
    OverflowBar(
        actions =
            listOf(
                OverflowAction("Edit", StxIcons.Edit, onClick = {}),
                OverflowAction("Delete", StxIcons.Delete, onClick = {}),
            ),
        modifier = Modifier.width(200.dp),
        maxVisible = 1,
    )
    CopyField(value = "ord_9f3a", label = "Order")
    LabeledProgress(progress = 0.45f, caption = "Uploading")
    TriStateCheckbox(state = CheckState.Indeterminate, onClick = {}, label = "All invoices")
    Disclosure(title = "Details", expanded = true, onExpandedChange = {}) {
        Typography(text = "Body")
    }
    SelectionBar(count = 3, onClear = {})
}
