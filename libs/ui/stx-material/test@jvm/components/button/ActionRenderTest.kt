package com.softistx.material.components.button

import androidx.compose.runtime.Composable
import com.softistx.material.components.content.FilterBar
import com.softistx.material.components.content.LabeledDivider
import com.softistx.material.components.content.Rating
import com.softistx.material.components.content.Stat
import com.softistx.material.components.feedback.LoadingMark
import com.softistx.material.components.form.UploadField
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.renderPage
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

private fun render(content: @Composable () -> Unit): Int = renderPage(480, 1100, content)

@Composable
private fun paint() {
    Fab(icon = StxIcons.Add, description = "New", onClick = {})
    Fab(
        icon = StxIcons.Add,
        description = "New order",
        onClick = {},
        text = "New order",
        expanded = true,
    )
    FabMenu(
        expanded = false,
        onExpandedChange = {},
        actions = listOf(FabAction("Edit", StxIcons.Edit, onClick = {})),
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
