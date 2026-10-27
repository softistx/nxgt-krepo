package com.softistx.material.components.content

import androidx.compose.runtime.Composable
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.button.ConfirmButton
import com.softistx.material.components.datetime.RelativeTime
import com.softistx.material.components.form.InlineEdit
import com.softistx.material.components.form.PasswordMeter
import com.softistx.material.renderPage
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

private fun render(content: @Composable () -> Unit): Int = renderPage(480, 1400, content)

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
