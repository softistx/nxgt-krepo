package com.strange.material.navigation

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
import com.strange.material.button.Button
import com.strange.material.button.ButtonColor
import com.strange.material.button.Fab
import com.strange.material.button.IconBadge
import com.strange.material.button.ViewMode
import com.strange.material.button.ViewToggle
import com.strange.material.data.SortControl
import com.strange.material.data.SortDirection
import com.strange.material.form.DangerZone
import com.strange.material.form.FormSection
import com.strange.material.form.ThemeToggle
import com.strange.material.icon.StrangeIcons
import com.strange.material.surface.HelpTip
import com.strange.material.text.Typography
import com.strange.material.theme.ColorMode
import com.strange.material.theme.StrangeTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan

class SettingsRenderTest :
    FeatureSpec({
        feature("every new settings and chrome control") {
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
    ThemeToggle(value = ColorMode.System, onChange = {})
    SortControl(
        options = listOf("Customer", "Total"),
        selected = "Customer",
        direction = SortDirection.Asc,
        onSelectedChange = {},
        onDirectionChange = {},
    )
    ViewToggle(value = ViewMode.List, onChange = {})
    BottomBar(fab = { Fab(icon = StrangeIcons.Add, description = "New", onClick = {}) }) {
        IconBadge(icon = StrangeIcons.Inbox, description = "Inbox", onClick = {}, count = 3)
    }
    HelpTip(text = "Payouts land the next working day.")
    FormSection(title = "Account") { Typography(text = "Email") }
    DangerZone(text = "This cannot be undone.") {
        Button(text = "Delete", onClick = {}, color = ButtonColor.Danger)
    }
    StepFooter(onNext = {}, onBack = {})
}
