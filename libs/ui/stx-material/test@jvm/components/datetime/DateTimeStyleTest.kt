package com.softistx.material.components.datetime

import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.softistx.material.Corner
import com.softistx.material.NodeBounds
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.painted
import com.softistx.material.paintedWhenDisabled
import com.softistx.material.probed
import com.softistx.material.renderProbe
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

private typealias DatePart = @Composable (Modifier, Style, Boolean) -> Unit

private val fields: Map<String, DatePart> =
    mapOf(
        "DateField" to { m, s, e -> DateField(LocalDate(2026, 10, 27), {}, m, enabled = e, style = s) },
        "DateRangeField" to { m, s, e -> DateRangeField(LocalDate(2026, 10, 27), null, { _, _ -> }, m, enabled = e, style = s) },
        "TimeField" to { m, s, e -> TimeField(LocalTime(9, 30), {}, m, enabled = e, style = s) },
    )

private fun corner(
    name: String,
    style: Style,
    enabled: Boolean,
): Color {
    val bounds = NodeBounds()
    val part = fields.getValue(name)
    return renderProbe(width = 400, height = 300, content = {
        StxTheme(strings = StxStrings.En, isDark = false) { part(Modifier.probed(bounds), style, enabled) }
    }) { settle().pixelAt(bounds, Corner.TopLeft) }
}

/**
 * The three fields are M3 text fields: they hand `style` to the field and add no alpha, because M3
 * dims a disabled field itself. `Calendar` is M3's `DatePicker` and takes the style on its modifier.
 */
class DateTimeStyleTest :
    FeatureSpec({
        feature("the date and time fields") {
            scenario("the caller's style reaches the field, in exact red, enabled or not") {
                fields.keys.forEach { name ->
                    withClue("$name enabled") { corner(name, painted, enabled = true) shouldBe Color.Red }
                    withClue("$name disabled") { corner(name, painted, enabled = false) shouldBe Color.Red }
                }
            }

            scenario("a disabled block paints only while disabled, and still in exact red (no fade added)") {
                fields.keys.forEach { name ->
                    withClue("$name enabled") { corner(name, paintedWhenDisabled, enabled = true) shouldBe Color.White }
                    withClue("$name disabled") { corner(name, paintedWhenDisabled, enabled = false) shouldBe Color.Red }
                }
            }
        }

        feature("Calendar") {
            scenario("the caller's style reaches the picker: alpha 0 hides it, the default shows it") {
                fun ink(style: Style) =
                    renderProbe(width = 400, height = 600, content = {
                        StxTheme(strings = StxStrings.En, isDark = false) { Calendar(LocalDate(2026, 10, 27), {}, style = style) }
                    }) { settle().ink() }
                ink(Style) shouldBeGreaterThan 1000
                ink(Style { alpha(0f) }) shouldBe 0
            }
        }
    })
