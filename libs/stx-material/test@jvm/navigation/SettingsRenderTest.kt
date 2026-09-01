package com.softistx.material.navigation

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
import com.softistx.material.button.Button
import com.softistx.material.button.ButtonColor
import com.softistx.material.button.Fab
import com.softistx.material.button.IconBadge
import com.softistx.material.button.ViewMode
import com.softistx.material.button.ViewToggle
import com.softistx.material.data.SortControl
import com.softistx.material.data.SortDirection
import com.softistx.material.form.DangerZone
import com.softistx.material.form.FormSection
import com.softistx.material.form.ThemeToggle
import com.softistx.material.icon.StxIcons
import com.softistx.material.surface.HelpTip
import com.softistx.material.text.Typography
import com.softistx.material.theme.ColorMode
import com.softistx.material.theme.StxTheme
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
    ThemeToggle(value = ColorMode.System, onChange = {})
    SortControl(
        options = listOf("Customer", "Total"),
        selected = "Customer",
        direction = SortDirection.Asc,
        onSelectedChange = {},
        onDirectionChange = {},
    )
    ViewToggle(value = ViewMode.List, onChange = {})
    BottomBar(fab = { Fab(icon = StxIcons.Add, description = "New", onClick = {}) }) {
        IconBadge(icon = StxIcons.Inbox, description = "Inbox", onClick = {}, count = 3)
    }
    HelpTip(text = "Payouts land the next working day.")
    FormSection(title = "Account") { Typography(text = "Email") }
    DangerZone(text = "This cannot be undone.") {
        Button(text = "Delete", onClick = {}, color = ButtonColor.Danger)
    }
    StepFooter(onNext = {}, onBack = {})
}
