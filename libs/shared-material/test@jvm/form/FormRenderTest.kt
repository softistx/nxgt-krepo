package com.strange.material.form

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
import com.strange.material.theme.StrangeTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * Every form control, composed once, for the class of failure that compiles and then throws at
 * render time — a menu anchored to nothing, a decoration box measuring badly, a padding driven
 * negative. The library met that class twice already, and each time only by running the catalogue
 * and clicking to the right story.
 *
 * It also pins the one behaviour that is worth seeing rather than reasoning about: an untouched
 * required field paints no error, and the same field paints one once it has been left.
 */
class FormRenderTest :
    FeatureSpec({
        feature("every control in the package") {
            scenario("renders without throwing, and paints something") {
                render { paint(touched = false) } shouldBeGreaterThan 0
            }
        }

        feature("a required field that has not been visited") {
            scenario("paints no more than the same field with nothing to say") {
                val quiet = render { paint(touched = false) }
                val complaining = render { paint(touched = true) }

                // The error line is ink that was not there before; nothing else changed.
                (complaining > quiet) shouldBe true
            }
        }
    })

/** How many pixels are not the page — a crude but honest measure of "something was drawn". */
private fun render(content: @Composable () -> Unit): Int {
    val scene = ImageComposeScene(width = 420, height = 900)
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
private fun paint(touched: Boolean) {
    val name = rememberField("", Rules.required())
    val notes = rememberField("", Rules.maxLength(160))
    val country = rememberField<String?>(null, Rules.chosen())
    val terms = rememberField(false, Rules.checked())
    val digest = rememberField(true)
    val plan = rememberField<String?>("Monthly")
    val channels = rememberField(setOf("Email"))
    val budget = rememberField(40f)
    val code = rememberField("12")
    if (touched) name.touch()

    TextField(field = name, label = "Full name")
    TextareaField(field = notes, label = "Notes", maxLength = 160, minLines = 2, maxLines = 3)
    SelectField(field = country, options = listOf("France", "Peru"), label = "Country")
    Checkbox(field = terms, label = "I accept the terms")
    Switch(field = digest, label = "Weekly digest")
    RadioGroup(field = plan, options = listOf("Monthly", "Yearly"), label = "Billing")
    CheckboxGroup(field = channels, options = listOf("Email", "SMS"), label = "Tell me by")
    SliderField(field = budget, label = "Budget")
    OtpField(field = code, length = 4, label = "Code")
    InputGroup { TextField(field = notes, label = "Voucher") }
}
