package com.softistx.material.components.navigation

import androidx.compose.runtime.Composable
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonColor
import com.softistx.material.components.button.Fab
import com.softistx.material.components.button.IconBadge
import com.softistx.material.components.button.ViewMode
import com.softistx.material.components.button.ViewToggle
import com.softistx.material.components.data.SortControl
import com.softistx.material.components.data.SortDirection
import com.softistx.material.components.form.DangerZone
import com.softistx.material.components.form.FormSection
import com.softistx.material.components.form.ThemeToggle
import com.softistx.material.components.surface.HelpTip
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.ColorMode
import com.softistx.material.renderPage
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

private fun render(content: @Composable () -> Unit): Int = renderPage(480, 1400, content)

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
