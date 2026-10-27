package com.softistx.material.components.form

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.disabled
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.foundation.theme.ColorMode
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

private val painted = Style { background(Color.Red) }
private val paintedWhenDisabled = Style { disabled { background(Color.Red) } }

/** A form part, drawn at [modifier] with the caller's [style] and the given [enabled]. */
private typealias FormPart = @Composable (modifier: Modifier, style: Style, enabled: Boolean) -> Unit

private val parts: Map<String, FormPart> =
    mapOf(
        "TextField" to { m, s, e -> TextField("", {}, m, label = "Name", enabled = e, style = s) },
        "TextareaField" to { m, s, e -> TextareaField("", {}, m, label = "Bio", enabled = e, style = s) },
        "Autocomplete" to { m, s, e ->
            Autocomplete("", {}, listOf("Ada"), {}, m, label = "Who", enabled = e, style = s)
        },
        "SelectField" to { m, s, e -> SelectField("A", {}, listOf("A", "B"), m, label = "Pick", enabled = e, style = s) },
        "CopyField" to { m, s, _ -> CopyField("key", m, label = "Key", style = s) },
        "Composer" to { m, s, e -> Composer("", {}, {}, m, enabled = e, style = s) },
        "Checkbox" to { m, s, e -> Checkbox(false, {}, "Terms", m, enabled = e, style = s) },
        "TriStateCheckbox" to { m, s, e -> TriStateCheckbox(CheckState.Off, {}, "All", m, enabled = e, style = s) },
        "Switch" to { m, s, e -> Switch(false, {}, "Alerts", m, enabled = e, style = s) },
        "RadioGroup" to { m, s, e -> RadioGroup("A", {}, listOf("A"), m, enabled = e, style = s) },
        "CheckboxGroup" to { m, s, e -> CheckboxGroup(setOf("A"), {}, listOf("A"), m, enabled = e, style = s) },
        "Checklist" to { m, s, e -> Checklist(listOf(CheckItem("One")), {}, m, enabled = e, style = s) },
        "SliderField" to { m, s, e -> SliderField(40f, {}, m, enabled = e, style = s) },
        "SliderField range" to { m, s, e -> SliderField(20f..60f, {}, m, enabled = e, style = s) },
        "QuantityField" to { m, s, e -> QuantityField(1, {}, m, enabled = e, style = s) },
        "InlineEdit" to { m, s, e -> InlineEdit("Title", {}, m, enabled = e, style = s) },
        "TagField" to { m, s, e -> TagField(emptyList(), {}, m, enabled = e, style = s) },
        "OtpField" to { m, s, e -> OtpField("12", {}, m, enabled = e, style = s) },
        "UploadField" to { m, s, e -> UploadField({}, m, enabled = e, style = s) },
        "ThemeToggle" to { m, s, e -> ThemeToggle(ColorMode.System, {}, m, enabled = e, style = s) },
    )

/** The toggles and the drop zone, which give under a press; fields and sliders do not. */
private val pressed = listOf("Checkbox", "TriStateCheckbox", "Switch", "RadioGroup", "CheckboxGroup", "Checklist", "UploadField")

/** Where to read a part: the corner, except where the styled node does not reach it. */
private val at: Map<String, Corner> = parts.keys.associateWith { Corner.TopLeft } + ("InlineEdit" to Corner.Center)

/** Paints [name] with [style] and answers its top-left pixel, at rest and (optionally) under the pointer. */
private fun corner(
    name: String,
    style: Style,
    enabled: Boolean = true,
    press: Boolean = false,
): Color {
    val bounds = NodeBounds()
    val part = parts.getValue(name)
    return renderProbe(
        width = 400,
        height = 300,
        content = { StxTheme(isDark = false) { part(Modifier.padding(20.dp).probed(bounds), style, enabled) } },
    ) {
        // Settle first: the node's bounds are only known after the first frame, and a press aims at them.
        val rest = settle()
        val grid = if (press) press(bounds) else rest
        grid.pixelAt(bounds, at.getValue(name), inset = 0)
    }
}

/**
 * Every interactive form part takes the caller's `style` and applies it last, onto the part's own
 * surface, with the enabled state fed to it. The toggles and the drop zone also give under a press.
 */
@OptIn(ExperimentalTestApi::class)
class FormStyleTest :
    FeatureSpec({
        feature("the caller's style") {
            scenario("a background painted by the style reaches the surface of every part") {
                parts.keys.forEach { name -> withClue(name) { corner(name, painted) shouldBe Color.Red } }
            }

            scenario("a disabled block paints when enabled = false and stays off while enabled") {
                // CopyField has no enabled parameter: it is a read-only field, never disabled.
                // A part that fades when disabled shows the red faded, so "not white" is the claim.
                (parts.keys - "CopyField").forEach { name ->
                    withClue("$name disabled") { (corner(name, paintedWhenDisabled, enabled = false) != Color.White) shouldBe true }
                    withClue("$name enabled") { corner(name, paintedWhenDisabled, enabled = true) shouldBe Color.White }
                }
            }
        }

        feature("the press") {
            scenario("a toggle row and the drop zone scale down, so the corner is no longer fully painted") {
                pressed.forEach { name ->
                    withClue(name) {
                        corner(name, painted) shouldBe Color.Red
                        (corner(name, painted, press = true) != Color.Red) shouldBe true
                    }
                }
            }

            scenario("a field does not scale: the corner stays painted under the pointer") {
                listOf("TextField", "SelectField", "OtpField").forEach { name ->
                    withClue(name) { corner(name, painted, press = true) shouldBe Color.Red }
                }
            }
        }

        feature("Autocomplete complaint") {
            scenario("supportingText shows under the field, like the other fields") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            Autocomplete("", {}, listOf("Ada"), {}, label = "Who", isError = true, supportingText = "Pick someone")
                        }
                    }
                    onNodeWithText("Pick someone").assertExists()
                }
            }
        }

        feature("the signature") {
            scenario("signature guard: a trailing optionLabel or format lambda still compiles after style") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            Autocomplete("", {}, listOf(1), {}) { "n$it" }
                            SelectField(1, {}, listOf(1, 2)) { "n$it" }
                            RadioGroup(1, {}, listOf(1, 2)) { "n$it" }
                            CheckboxGroup(setOf(1), {}, listOf(1, 2)) { "n$it" }
                            SliderField(40f, {}) { "${it.toInt()} %" }
                            SliderField(20f..60f, {}) { "${it.toInt()} %" }
                        }
                    }
                }
            }
        }
    })
