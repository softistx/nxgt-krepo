package com.softistx.material.components.button

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.feedback.LabeledProgress
import com.softistx.material.components.form.CheckState
import com.softistx.material.components.form.CopyField
import com.softistx.material.components.form.TriStateCheckbox
import com.softistx.material.components.layout.SelectionBar
import com.softistx.material.components.surface.Disclosure
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography
import com.softistx.material.renderPage
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

private fun render(content: @Composable () -> Unit): Int = renderPage(480, 1200, content)

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
