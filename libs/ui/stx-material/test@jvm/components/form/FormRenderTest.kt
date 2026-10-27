package com.softistx.material.components.form

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import com.softistx.material.renderPage
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * Every form control, composed once, for the class of failure that compiles and then throws at
 * render time — a menu anchored to nothing, a decoration box measuring badly, a padding driven
 * negative. The library met that class twice already, and each time only by running the catalogue
 * and clicking to the right story.
 *
 * It also pins the one behaviour that is worth seeing rather than reasoning about: a field given no
 * complaint paints no error line, and the same field paints one once it is given one.
 */
class FormRenderTest :
    FeatureSpec({
        feature("every control in the package") {
            scenario("renders without throwing, and paints something") {
                render { paint(complaining = false) } shouldBeGreaterThan 0
            }
        }

        feature("a field with nothing to say") {
            scenario("paints less than the same field carrying a complaint") {
                val quiet = render { paint(complaining = false) }
                val complaining = render { paint(complaining = true) }

                // The error line is ink that was not there before; nothing else changed.
                (complaining > quiet) shouldBe true
            }
        }
    })

/** How many pixels are not the page — a crude but honest measure of "something was drawn". */
private fun render(content: @Composable () -> Unit): Int = renderPage(420, 1100, content)

@Composable
private fun paint(complaining: Boolean) {
    val complaint = "This field is required".takeIf { complaining }

    TextField(
        value = "",
        onValueChange = {},
        label = "Full name",
        isError = complaining,
        supportingText = complaint,
    )
    TextareaField(
        value = "",
        onValueChange = {},
        label = "Notes",
        maxLength = 160,
        minLines = 2,
        maxLines = 3,
    )
    SelectField(
        value = null,
        onValueChange = {},
        options = listOf("France", "Peru"),
        label = "Country",
    )
    Checkbox(value = false, onValueChange = {}, label = "I accept the terms")
    Switch(value = true, onValueChange = {}, label = "Weekly digest")
    RadioGroup(
        value = "Monthly",
        onValueChange = {},
        options = listOf("Monthly", "Yearly"),
        label = "Billing",
    )
    CheckboxGroup(
        value = setOf("Email"),
        onValueChange = {},
        options = listOf("Email", "SMS"),
        label = "Tell me by",
    )
    SliderField(value = 40f, onValueChange = {}, label = "Budget")
    OtpField(value = "12", onValueChange = {}, length = 4, label = "Code")
    InputGroup { TextField(value = "", onValueChange = {}, label = "Voucher") }
    UploadField(onClick = {})
}
