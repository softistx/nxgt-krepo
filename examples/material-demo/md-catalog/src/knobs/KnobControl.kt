package com.softistx.material.demo.knobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.display.Chip
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

/**
 * One row of the control panel. Material 3 supplies the switch, the slider and the text field:
 * stx-material has no form layer until phase 2, and the demo says so rather than pretending
 * otherwise.
 */
@Composable
fun KnobControl(
    knobs: Knobs,
    knob: Knob,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
    ) {
        when (knob) {
            is Knob.Flag -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(knob.label)
                    Switch(
                        checked = knobs.current(knob.label) as? Boolean ?: false,
                        onCheckedChange = { knobs.set(knob.label, it) },
                    )
                }
            }

            is Knob.Choice -> {
                Label(knob.label)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
                ) {
                    knob.options.forEach { option ->
                        Chip(
                            text = knob.render(option),
                            selected = knobs.current(knob.label) == option,
                            onClick = { knobs.set(knob.label, option) },
                        )
                    }
                }
            }

            is Knob.Text -> {
                OutlinedTextField(
                    value = knobs.current(knob.label) as? String ?: "",
                    onValueChange = { knobs.set(knob.label, it) },
                    label = { Typography(text = knob.label, variant = TypographyVariant.BodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is Knob.Number -> {
                Label("${knob.label} — ${format(knobs.current(knob.label) as? Float ?: knob.range.start)}")
                Slider(
                    value = knobs.current(knob.label) as? Float ?: knob.range.start,
                    onValueChange = { knobs.set(knob.label, it) },
                    valueRange = knob.range,
                    steps = knob.steps,
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Typography(text = text, variant = TypographyVariant.LabelMedium, emphasis = Emphasis.Medium)
}

private fun format(value: Float): String = ((value * 10f).toInt() / 10f).toString()
